# frozen_string_literal: true

begin
  require 'github_changelog_generator/task'
rescue LoadError
  task :changelog do
    abort('Run `bundle install --with release` to install the `github_changelog_generator` gem.')
  end
else
  GitHubChangelogGenerator::RakeTask.new :changelog do |config|
    config.header = <<~HEADER.chomp
      # Changelog

      All notable changes to this project will be documented in this file.
    HEADER
    config.user = 'openvoxproject'
    config.project = 'openvoxdb'
    config.exclude_labels = %w[dependencies duplicate question invalid wontfix wont-fix modulesync skip-changelog]
    # this is probably the worst way to do this
    # ideally there would be a VERSION file and clojure and Rake would read it
    config.future_release = File.readlines('project.clj').first.scan(/".*"/).first.gsub('"', '')
    # we limit the changelog to all new openvox releases, to skip perforce onces
    # otherwise the changelog generate takes a lot amount of time
    config.since_tag = '8.9.1'
    #config.exclude_tags_regex = /\A7\./
    config.release_branch = 'main'
  end
end
