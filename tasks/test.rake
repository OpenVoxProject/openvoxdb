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
      spec = args[:spec] || 'core+ext/openjdk17/pg-17'
      _suite, java, _pg = spec.split('/')

      image = java =~ /17/ ? "ruby:3.2-bookworm" : "ruby:3.2-trixie"

      puts "Starting container"
      teardown if container_exists
      start_container(image)

      # The tests make tons of temp dirs in the current working directory, and trying to do this
      # inside the volume mount dir results in permissions issues. So we copy all of the code to /tmp/code
      # and run from there. Not ideal, but we have to since it curls down pdbbox and we don't control that.
      run("cp -r /code /tmp && chown -R root:root /tmp/code")
      run("apt update && apt install -y leiningen curl python3 procps")
      run("cd /tmp/code && rm -rf ci/local && ext/bin/prep-debianish-root --for #{spec} --install ci/local")
      run('echo "postgres ALL=(ALL:ALL) NOPASSWD:ALL" >> /etc/sudoers')
      # There is a non-fatal error when running on an arm64 host, so we can ignore exit 255.
      run("cd /tmp/code && ci/bin/prep-and-run-in local #{spec}", allowed_exit_codes: [0, 255])
      run("chown -R postgres:postgres /tmp/code")
      run("cd /tmp/code && NO_ACCEPTANCE=true ci/bin/run #{spec}", 'postgres')
    ensure
      #teardown
    end
  end
end
