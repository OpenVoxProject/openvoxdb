def vox_set_version(version)
  file='project.clj'
  data = File.read(file)

  v = data[/^\(defproject\s+\S+\s+"([^"]+)"/m, 1]
  abort("Couldn't find defproject version string in #{file}") unless v

  re = %r,\[org\.openvoxproject/puppetdb\s+"[^"]+\"\],
  abort("Couldn't find literal [org.openvoxproject/puppetdb "..."]] in #{file}") unless data.match?(re)

  data.sub!(
    %r{\(defproject org.openvoxproject/puppetdb ".*-SNAPSHOT"},
    "(defproject org.openvoxproject/puppetdb \"#{version}-SNAPSHOT\""
  )

  data.gsub!(re, %,[org.openvoxproject/puppetdb "#{version}-SNAPSHOT"],)

  File.write(file, data)
end

namespace :vox do
  desc 'Update the version in preparation for a release'
  task 'version:bump:full', [:version] do |_, args|
    abort 'You must provide a tag.' if args[:version].nil? || args[:version].empty?
    version = args[:version]
    #VERSION_PATTERN = '[0-9]+(?>\.[0-9a-zA-Z]+)*(-[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?' # :nodoc:
    #ANCHORED_VERSION_PATTERN = /\A\s*(#{VERSION_PATTERN})?\s*\z/ # :nodoc:
    abort "#{version} does not appear to be a valid version string in x.y.z format" unless Gem::Version.correct?(version)

    puts "Setting version to #{version}"

    vox_set_version args[:version]

  end
end
