# Currently only tested with core+ext/openjdk17/pg-17. May need tweaks for others.

@container = "openvoxdb-test"

def container_exists
  !`docker container ls --all --filter 'name=#{@container}' --format json`.strip.empty?
end

def start_container(image)
  run_command("docker run -d --name #{@container} -v .:/code #{image} /bin/sh -c 'tail -f /dev/null'", silent: false, print_command: true)
end

def teardown
  if container_exists
    puts "Stopping #{@container}"
    run_command("docker stop #{@container}", silent: false, print_command: true)
    run_command("docker rm #{@container}", silent: false, print_command: true)
  end
end

def run(cmd, user='root', allowed_exit_codes: [0])
  run_command("docker exec -u #{user} #{@container} /bin/bash --login -c '#{cmd}'", silent: false, print_command: true, allowed_exit_codes: allowed_exit_codes)
end

namespace :vox do
  desc 'Run lein test locally in the same way that PR checks run with a properly configured postgres and other artifacts.'
  task :test, [:spec] do |_, args|
    begin
      suite, java, pg = args[:spec]&.split('/') || ['core+ext', 'openjdk17', 'pg-17']

      image = java =~ /17/ ? "debian:12" : "debian:13"

      puts "Starting container"
      teardown if container_exists
      start_container(image)

      run("apt update && apt install -y leiningen curl python3 procps")
      run("cd /code && rm -rf ci/local && ext/bin/prep-debianish-root --for #{args[:spec]} --install ci/local")
      run('echo "postgres ALL=(ALL:ALL) NOPASSWD:ALL" >> /etc/sudoers')
      # There is a non-fatal error when running on an arm64 host, so we can ignore exit 255.
      run("cd /code && ci/bin/prep-and-run-in local #{args[:spec]}", allowed_exit_codes: [0, 255])
      run("cd /code && NO_ACCEPTANCE=true ci/bin/run #{args[:spec]}", 'postgres')
    ensure
      teardown
    end
  end
end
