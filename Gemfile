gemfile_home = File.dirname(__FILE__)

source ENV['GEM_SOURCE'] || 'https://rubygems.org'
oldest_supported_openvox = '8.19.0'
beaker_version = ENV['BEAKER_VERSION']

puppet_ref = if File.exist?(gemfile_home + '/ext/test-conf/puppet-ref-requested')
               File.read(gemfile_home + '/ext/test-conf/puppet-ref-requested').strip
            elsif File.exist?(gemfile_home + '/ext/test-conf/puppet-ref-default')
              File.read(gemfile_home + '/ext/test-conf/puppet-ref-default').strip
            else
              'main'
            end

def location_for(place)
  if place =~ /^(git[:@][^#]*)#(.*)/
    [{ git: $1, branch: $2, require: false }]
  elsif place =~ /^file:\/\/(.*)/
    ['>= 0', { path: File.expand_path($1), require: false }]
  else
    [place, { require: false }]
  end
end

gem 'openfact'
gem 'rake'
gem 'packaging', '~> 1.0', github: 'OpenVoxProject/packaging'

group :test do
  # Add test-unit for ruby 2.2+ support (has been removed from stdlib)
  gem 'test-unit'

  gem 'rspec'

  # FIXME: going to version 1.0.0 breaks a lot of rspec tests. The changelog
  # doesn't list any breaking changes, so we'll need to investigate more.
  gem 'puppetlabs_spec_helper', '0.10.3'

  case puppet_ref
  when 'latest'
    gem 'openvox', ">= #{oldest_supported_openvox}", :require => false
  when 'oldest'
    gem 'openvox', oldest_supported_openvox, :require => false
  else
    gem 'openvox', :git => 'https://github.com/OpenVoxProject/openvox.git', :ref => puppet_ref, :require => false
  end
  # syslog is a dependency of puppet/openvox, but missing in their gemspec
  gem 'syslog'
  gem 'puppet-pson', '~> 1.1'

  gem 'mocha', '~> 1.0'
end

group :development do
  gem 'httparty'
end

# This is a workaround for a bug in bundler, where it likes to look at ruby
# version deps regardless of what groups you want or not. This lets us
# conditionally shortcut evaluation entirely.
if ENV['NO_ACCEPTANCE'] != 'true'
  group :acceptance do
    if beaker_version
      #use the specified version
      gem 'beaker', *location_for(beaker_version)
    else
      # use the pinned version
      gem 'beaker', '~> 6.0'
    end
    gem 'beaker-hostgenerator', '~> 2.4'
    gem 'beaker-puppet', '~> 4.0'
  end
end

group :release, optional: true do
  gem 'faraday-retry', '~> 2.1', require: false
  # gem 'github_changelog_generator', '~> 1.16.4', require: false
  gem 'github_changelog_generator', github: 'smortex/github-changelog-generator', branch: 'avoid-processing-a-single-commit-multiple-time', require: false
end
