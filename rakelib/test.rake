# frozen_string_literal: true

# Tests OpenVoxDB in the same manner as we do in PR tests.
# Only tested with a handful of the cells we use in CI. May need adjustment for others.
# 
# Env var options:
#   NO_TEARDOWN - If set, do not stop the docker container after the tests for debugging purposes.

require_relative 'utils/docker_runner'

module Vox
  class Test
    def initialize(spec:)
      @spec = spec
      @runner = Vox::DockerRunner.new(container_name: 'openvoxdb-test', image: select_image(@spec))
    end

    def run_tests
      @runner.teardown if @runner.container_exists?
      @runner.start(volumes: [[Dir.pwd, '/code']])

      # The tests make tons of temp dirs in the current working directory, and trying to do this
      # inside the volume mount dir results in permissions issues. So we copy all of the code to /tmp/code
      # and run from there. Not ideal, but we have to since it curls down pdbbox and we don't control that.
      @runner.exec('cp -r /code /tmp && chown -R root:root /tmp/code')
      @runner.exec('apt update && apt install -y leiningen curl python3 procps')
      @runner.exec("cd /tmp/code && rm -rf ci/local && ext/bin/prep-debianish-root --for #{@spec} --install ci/local")
      @runner.exec('echo "postgres ALL=(ALL:ALL) NOPASSWD:ALL" >> /etc/sudoers')
      # There is a non-fatal error when running on an arm64 host, so we can ignore exit 255.
      @runner.exec("cd /tmp/code && ci/bin/prep-and-run-in local #{@spec}", allowed_exit_codes: [0, 255])
      @runner.exec('chown -R postgres:postgres /tmp/code')
      @runner.exec("cd /tmp/code && NO_ACCEPTANCE=true ci/bin/run #{@spec}", user: 'postgres')
    ensure
      @runner.teardown unless ENV['NO_TEARDOWN']
    end

    private

    def select_image(spec)
      _suite, java, _pg = spec.split('/')
      java =~ /17/ ? 'ruby:3.2-bookworm' : 'ruby:3.2-trixie'
    end
  end
end

namespace :vox do
  desc 'Run lein test locally in the same way that PR checks run with a properly configured postgres and other artifacts.'
  task :test, [:spec] do |_, args|
    Vox::Test.new(spec: (args[:spec] || 'core+ext/openjdk17/pg-17')).run_tests
  end
end
