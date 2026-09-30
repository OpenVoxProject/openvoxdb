# frozen_string_literal: true

require 'json'
require 'open3'
require_relative 'utils/shell'

# Smoke test for the packages that vox:build leaves in output. It installs the
# packages for one vanagon build target, such as el-9-x86_64, in a container
# that runs systemd, and starts the service against a PostgreSQL container.
# The container is the image the vanagon platform defaults name for that
# target, on that target's architecture. The FIPS packages run on a regular
# kernel here, which covers install and startup.

# DOCKER_BIN swaps in another container engine
SMOKE_DOCKER = ENV.fetch('DOCKER_BIN', 'docker').split.freeze
SMOKE_CONTAINER = 'openvoxdb-smoke'
# PostgreSQL runs in a second container on the network of the first, so the service reaches it on localhost
SMOKE_POSTGRES_CONTAINER = 'openvoxdb-smoke-postgres'
SMOKE_POSTGRES_IMAGE = 'postgres:18'
# The agent is installed from this repository, the packages depend on it
SMOKE_COLLECTION = 'openvox9'
# The architectures GitHub has runners for, as vanagon spells them
SMOKE_ARCHES = %w[amd64 x86_64 aarch64].freeze
# Images to use instead of the vanagon default, which for sles-15 is an old service pack kept for building
SMOKE_IMAGES = { 'sles-15-x86_64' => 'registry.suse.com/suse/sle15:15.7' }.freeze

# Runs docker without a shell in between, so the arguments need no quoting
def smoke_docker(*args)
  command = [*SMOKE_DOCKER, *args]
  puts "#{Vox::Shell::GREEN}Running #{command.join(' ')}#{Vox::Shell::RESET}"
  abort "#{Vox::Shell::RED}Command failed! Command: #{command.join(' ')}#{Vox::Shell::RESET}" unless system(*command)
end

def smoke_exec(script)
  smoke_docker('exec', SMOKE_CONTAINER, '/bin/bash', '-c', script)
end

# The container installs systemd before it hands over to it, so this can take a while
def smoke_wait_for_systemd
  puts "Waiting for systemd in #{SMOKE_CONTAINER}"
  state = nil
  60.times do
    state, = Open3.capture2e(*SMOKE_DOCKER, 'exec', SMOKE_CONTAINER, 'systemctl', 'is-system-running')
    # systemd reports degraded when a unit failed, which some do in a container. The state is
    # matched as a line because the container engine may print warnings of its own around it.
    return if state.match?(/^(running|degraded)$/)

    sleep 5
  end
  abort "#{Vox::Shell::RED}systemd did not come up in #{SMOKE_CONTAINER}, last state: #{state.strip}#{Vox::Shell::RESET}"
end

# The service refuses to migrate without the pg_trgm extension and does not create it itself.
# PostgreSQL initializes its data directory first, so this waits for it as well.
def smoke_create_extension
  puts "Waiting for PostgreSQL in #{SMOKE_POSTGRES_CONTAINER}"
  output = nil
  60.times do
    output, status = Open3.capture2e(*SMOKE_DOCKER, 'exec', SMOKE_POSTGRES_CONTAINER, 'psql', '--username', 'puppetdb',
                                     '--dbname', 'puppetdb', '--command', 'CREATE EXTENSION pg_trgm')
    return if status.success?

    sleep 5
  end
  abort "#{Vox::Shell::RED}Could not create the extension in #{SMOKE_POSTGRES_CONTAINER}, last output: #{output.strip}#{Vox::Shell::RESET}"
end

# The vanagon platform defaults name the image and the docker platform of every build
# target. The gem sits in the packaging group, so it is only loaded when a task needs it.
def smoke_vanagon_defaults
  require 'vanagon/platform'
  File.join(Gem.loaded_specs['vanagon'].gem_dir, 'lib', 'vanagon', 'platform', 'defaults')
end

namespace :vox do
  desc 'Smoke test the packages in output for one vanagon target, for example vox:smoke[el-9-x86_64]'
  task :smoke, [:target] do |_, args|
    name = args[:target]
    abort 'You must provide a target, for example el-9-x86_64' if name.nil?
    defaults = smoke_vanagon_defaults
    target = Vanagon::Platform.load_platform(name, defaults)
    image = SMOKE_IMAGES.fetch(name, "#{target.docker_registry}/#{target.docker_image}")
    # The platform is the target without its architecture
    platform = name.sub(/-[^-]+$/, '')

    # Package file names have the platform without the dash, such as el9 or ubuntu24.04
    dist = platform.delete('-')
    packages = Dir.glob("output/**/*#{dist}*.{rpm,deb}")
    abort "Expected #{platform} packages in output, found none" if packages.empty?
    # The output directory is mounted at /output in the container
    packages = packages.map { |package| "/#{package}" }.join(' ')

    release = "#{SMOKE_COLLECTION}-release"
    case platform
    when /^(debian|ubuntu)/
      # Keeps the configuration of packages from asking questions
      install = 'DEBIAN_FRONTEND=noninteractive apt-get install -y'
      # systemd alone is enough here, the services it recommends are meant for full hosts
      install_systemd = "apt-get update && #{install} --no-install-recommends systemd"
      install_release = "#{install} ca-certificates curl && " \
                        "curl -fsSL -o /tmp/#{release}.deb https://apt.voxpupuli.org/#{release}-#{dist}.deb && " \
                        "#{install} /tmp/#{release}.deb && apt-get update"
    when /^sles/
      # zypper has to import the key of the repository without asking and to accept the unsigned packages in output
      install = 'zypper --non-interactive --gpg-auto-import-keys install --allow-unsigned-rpm'
      install_systemd = "#{install} systemd"
      # zypper cannot check the release package, because the key it is signed with only arrives with that package
      install_release = 'zypper --non-interactive --no-gpg-checks install ' \
                        "https://yum.voxpupuli.org/#{release}-#{platform}.noarch.rpm"
    else
      install = 'dnf install -y'
      install_systemd = "#{install} systemd"
      install_release = "#{install} https://yum.voxpupuli.org/#{release}-#{platform}.noarch.rpm"
    end

    begin
      # systemd reads the container variable to detect that it runs in a container
      smoke_docker('run', '--detach', '--name', SMOKE_CONTAINER,
                   '--platform', target.docker_arch, '--privileged', '--env', 'container=docker',
                   '--volume', "#{File.expand_path('output')}:/output:ro",
                   image, '/bin/sh', '-c', "#{install_systemd} && exec /usr/lib/systemd/systemd")
      smoke_docker('run', '--detach', '--name', SMOKE_POSTGRES_CONTAINER, '--network', "container:#{SMOKE_CONTAINER}",
                   '--env', 'POSTGRES_USER=puppetdb', '--env', 'POSTGRES_PASSWORD=puppetdb', '--env', 'POSTGRES_DB=puppetdb',
                   SMOKE_POSTGRES_IMAGE)
      smoke_create_extension
      smoke_wait_for_systemd
      smoke_exec(install_release)
      smoke_exec("#{install} openvox-agent")
      smoke_exec("#{install} #{packages}")
      # The package ships database.ini with every setting commented out
      smoke_exec(<<~SCRIPT)
        cat > /etc/puppetlabs/puppetdb/conf.d/database.ini <<'INI'
        [database]
        subname = //localhost:5432/puppetdb
        username = puppetdb
        password = puppetdb
        INI
      SCRIPT
      # The unit is of type notify, so this returns once the service reports that it is ready. The unit
      # lets a start take hours because of migrations, the bound here keeps a failure inside a CI job.
      smoke_exec('timeout 600 systemctl start puppetdb')
      puts "#{Vox::Shell::GREEN}The #{platform} packages passed the smoke test on #{name}#{Vox::Shell::RESET}"
    rescue SystemExit
      # Show what the containers and the service logged before the containers are removed
      system(*SMOKE_DOCKER, 'logs', SMOKE_CONTAINER)
      system(*SMOKE_DOCKER, 'logs', SMOKE_POSTGRES_CONTAINER)
      system(*SMOKE_DOCKER, 'exec', SMOKE_CONTAINER, 'journalctl', '--no-pager', '--unit', 'puppetdb')
      system(*SMOKE_DOCKER, 'exec', SMOKE_CONTAINER, 'cat', '/var/log/puppetlabs/puppetdb/puppetdb.log')
      raise
    ensure
      # PostgreSQL goes first, because it uses the network of the other container
      smoke_docker('rm', '--force', SMOKE_POSTGRES_CONTAINER, SMOKE_CONTAINER)
    end
  end

  namespace :smoke do
    desc 'List the vanagon targets to smoke test the packages in output on, as JSON'
    task :targets do
      # el-9 from output/el/9 and ubuntu-24.04 from output/deb/ubuntu24.04
      platforms = Dir.glob('output/**/*.{rpm,deb}').map do |package|
        os, version = package.split('/')[1, 2]
        os == 'deb' ? version.sub(/(\d)/, '-\1') : "#{os}-#{version}"
      end
      defaults = smoke_vanagon_defaults
      targets = platforms.uniq.sort.flat_map do |platform|
        # Only the architectures vanagon has a target for
        SMOKE_ARCHES.map { |arch| "#{platform}-#{arch}" }.select { |name| File.exist?(File.join(defaults, "#{name}.rb")) }
      end
      puts JSON.generate(targets)
    end
  end
end
