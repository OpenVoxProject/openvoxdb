# frozen_string_literal: true

require_relative 'shell'

module Vox
  class DockerRunner
    def initialize(container_name:, image:)
      @container_name = container_name
      @image = image
    end

    attr_reader :container_name, :image

    def image_exists?
      !`docker images -q #{image}`.strip.empty?
    end

    def container_exists?
      !`docker container ls --all --filter 'name=#{container_name}' --format json`.strip.empty?
    end

    def teardown
      return unless container_exists?
      puts "Stopping #{container_name}"
      Vox::Shell.run("docker stop #{container_name}", silent: false, print_command: true)
      Vox::Shell.run("docker rm #{container_name}", silent: false, print_command: true)
    end

    def start(volumes: [])
      puts "Starting container #{container_name}"
      vol_args = volumes.map { |host_path, container_path| "-v #{host_path}:#{container_path}" }.join(" ")
      Vox::Shell.run(
        "docker run -d --name #{container_name} #{vol_args} #{image} /bin/sh -c 'tail -f /dev/null'",
        silent: false,
        print_command: true
      )
    end

    def exec(cmd, user: nil, allowed_exit_codes: [0])
      user_arg = user ? "-u #{user} " : ""
      Vox::Shell.run(
        "docker exec #{user_arg}#{container_name} /bin/bash --login -c #{shell_single_quote(cmd)}",
        silent: false,
        print_command: true,
        allowed_exit_codes: allowed_exit_codes
      )
    end

    private

    def shell_single_quote(s)
      "'" + s.to_s.gsub("'", %q('"'"')) + "'"
    end
  end
end
