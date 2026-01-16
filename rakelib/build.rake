# frozen_string_literal: true

# Builds packages using a Docker container
# Env var options:
#  NO_TEARDOWN - If set, do not stop the docker container after the build for debugging purposes.
#  EZBAKE_BRANCH - If set, use this ezbake branch for building. If used, must also set EZBAKE_VERSION accordingly.
#  EZBAKE_REPO - If EZBAKE_BRANCH is set, use this repo URL for ezbake (default: https://github.com/openvoxproject/ezbake)
#  EZBAKE_VERSION - If set, use this version string in project.clj for ezbake. Must correspond to the version currently in defproject
#                   in EZBAKE_BRANCH.
#  FULL_DEP_REBUILD_BRANCH - If set, rebuild all dependencies, where all dependency repos have this branch present with your
#                            desired changes. Overrides any other DEP_ settings.
#  DEP_REBUILD - Comma-separated list of dependencies to rebuild from source. Should be the repo names.
#  DEP_REBUILD_BRANCH - If DEP_REBUILD is set, use this branch for the specified dependencies (default: main)
#  DEP_REBUILD_ORG - If DEP_REBUILD is set, use this GitHub org for the specified dependencies to build the repo URL (default: openvoxproject)
#  DEB_PLATFORMS - Comma-separated list of debian/ubuntu platforms to build for
#  RPM_PLATFORMS - Comma-separated list of rpm platforms to build for
#  FIPS - If set, build specified platforms with the appropriate 'fips' lein profile(s) enabled.

require 'fileutils'
require 'tmpdir'
require_relative 'utils/docker_runner'

module Vox
  class Build
    def platform_targets
      # It seems like these are special files/names that, when you want to add a new one, require
      # changes in some other component.  But no, it seems to only really look at the parts of
      # the text in the string, as long as it looks like "base-<whatever you want to call the platform>-i386.cow"
      # and "<doesn't matter>-<os>-<osver>-<arch which doesn't matter because it's actually noarch>".
      # I think it just treats all debs like Debian these days. And all rpms are similar.
      # So do whatever you want I guess. We really don't need separate packages for each platform.
      # To be fixed one of these days. Relevant stuff:
      #   https://github.com/puppetlabs/ezbake/blob/aeb7735a16d2eecd389a6bd9e5c0cfc7c62e61a5/resources/puppetlabs/lein-ezbake/template/global/tasks/build.rake
      #   https://github.com/puppetlabs/ezbake/blob/aeb7735a16d2eecd389a6bd9e5c0cfc7c62e61a5/resources/puppetlabs/lein-ezbake/template/global/ext/fpm.rb
      deb_platforms = ENV['DEB_PLATFORMS'] || 'ubuntu-20.04,ubuntu-22.04,ubuntu-24.04,ubuntu-25.04,debian-11,debian-12,debian-13'
      rpm_platforms = ENV['RPM_PLATFORMS'] || 'el-8,el-9,el-10,sles-15,sles-16,amazon-2,amazon-2023,fedora-42,fedora-43'

      debs = deb_platforms.split(',').map { |p| "base-#{p.split("-").join}-i386.cow" }.join(" ")
      rpms = rpm_platforms.split(',').map { |p| "pl-#{p}-x86_64" }.join(" ")

      [debs, rpms]
    end
    # The deps must be built in this order due to dependencies between them.
    # There is a circular dependency between clj-http-client and trapperkeeper-webserver-jetty10,
    # but only for tests, so the build *should* work.
    DEP_BUILD_ORDER = [
      'clj-parent',
      'clj-kitchensink',
      'clj-i18n',
      'comidi',
      'jvm-ssl-utils',
      'trapperkeeper',
      'trapperkeeper-filesystem-watcher',
      'trapperkeeper-webserver-jetty10',
      'trapperkeeper-authorization',
      'trapperkeeper-metrics',
      'trapperkeeper-status',
      'stockpile',
      'structured-logging',
    ].freeze

    def initialize(tag:)
      @tag = tag
      @runner = Vox::DockerRunner.new(container_name: 'openvoxdb-builder', image: 'ezbake-builder')
      @deps_tmp = Dir.mktmpdir('deps')
    end

    def build
      checkout_tag_if_requested
      build_image_unless_present
      start_container
      build_and_install_libs(prepare_deps)
      build_project
      postprocess_output
    ensure
      @runner.teardown unless ENV['NO_TEARDOWN']
      FileUtils.rm_rf(@deps_tmp)
    end

    private

    def checkout_tag_if_requested
      if @tag.nil? || @tag.empty?
        puts 'Running build with current branch'
      else
        puts "Running build on #{@tag}"
        Vox::Shell.run("git fetch --tags && git checkout #{@tag}")
      end
    end

    def build_image_unless_present
      return if @runner.image_exists?

      # If the Dockerfile has changed since this was last built,
      # delete all containers and do `docker rmi ezbake-builder`
      puts 'Building ezbake-builder image'
      Vox::Shell.run("docker build -t ezbake-builder .", silent: false, print_command: true)
    end

    def prepare_deps
      libs = {}

      # Manage ezbake override
      ezbake_branch = ENV.fetch('EZBAKE_BRANCH', '').strip
      unless ezbake_branch.empty?
        libs['ezbake'] = {
          repo: ENV.fetch('EZBAKE_REPO', 'https://github.com/openvoxproject/ezbake'),
          branch: ENV.fetch('EZBAKE_BRANCH', 'main'),
        }
      end

      # Decide if we're rebuilding everything or a subset
      full_rebuild_branch = ENV.fetch('FULL_DEP_REBUILD_BRANCH', '').strip
      subset_list = ENV.fetch('DEP_REBUILD', '').split(',').map(&:strip).reject(&:empty?)
      subset_branch = ENV.fetch('DEP_REBUILD_BRANCH', 'main').strip
      rebuild_org = ENV.fetch('DEP_REBUILD_ORG', 'openvoxproject').strip

      selected_libs = []
      selected_branch = nil

      if !full_rebuild_branch.empty?
        selected_branch = full_rebuild_branch
        selected_libs = DEP_BUILD_ORDER.dup
      elsif !subset_list.empty?
        selected_branch = subset_branch
        unknown = subset_list - DEP_BUILD_ORDER
        puts "WARNING: Unknown deps in DEP_REBUILD (will be ignored): #{unknown.join(', ')}" unless unknown.empty?
        selected_libs = DEP_BUILD_ORDER & subset_list # Keeps DEP_BUILD_ORDER ordering
      end

      selected_libs.each do |lib|
        libs[lib] = { repo: "https://github.com/#{rebuild_org}/#{lib}", branch: selected_branch }
      end

      libs.each do |lib, config|
        puts "Checking out #{lib}"
        Vox::Shell.run(
          "git clone --revision #{config[:branch]} #{config[:repo]} #{@deps_tmp}/#{lib}",
          silent: false,
          print_command: true
        )
      end

      libs
    end

    def start_container
      @runner.teardown if @runner.container_exists?
      volumes = [[Dir.pwd, '/code'], [@deps_tmp, '/deps']]
      @runner.start(volumes: volumes)
    end

    def build_and_install_libs(libs)
      libs.each_key do |lib|
        puts "Building and installing #{lib} from source"
        @runner.exec("cd /deps/#{lib} && lein install")
      end
    end

    def build_project
      debs, rpms = platform_targets
      fips = !ENV['FIPS'].nil?
      ezbake_version_var = ENV['EZBAKE_VERSION'] ? "EZBAKE_VERSION=#{ENV['EZBAKE_VERSION']}" : ""

      puts 'Building openvoxdb'
      @runner.exec('cd /code && rm -rf ruby output && bundle install --without test && lein install')
      @runner.exec(
        "cd /code && COW=\"#{debs}\" MOCK=\"#{rpms}\" GEM_SOURCE='https://rubygems.org' #{ezbake_version_var} " \
        "EZBAKE_ALLOW_UNREPRODUCIBLE_BUILDS=true EZBAKE_NODEPLOY=true LEIN_PROFILES=ezbake " \
        "lein with-profile #{fips ? "fips," : ""}user,ezbake,provided,internal ezbake local-build"
      )
    end

    def postprocess_output
      Vox::Shell.run("sudo chown -R $USER output", print_command: true)
      Dir.glob("output/**/*i386*").each { |f| FileUtils.rm_rf(f) }
      Dir.glob("output/puppetdb-*.tar.gz").each { |f| FileUtils.mv(f, f.sub('puppetdb', 'openvoxdb')) }
    end
  end
end

namespace :vox do
  desc 'Build openvoxdb packages with Docker'
  task :build, [:tag] do |_, args|
    Vox::Build.new(tag: args[:tag]).build
  end
end
