# Set the option vars provided by acceptance/options/postgres.rb
# without tying in the hardcoded pre-suite and test paths set
# in acceptance/options/common.rb
{
  :is_puppetserver        => 'true',
  :'use-service'          => 'true',
  :'puppetserver-confdir' => '/etc/puppetlabs/puppetserver/conf.d',
  :puppetservice          => 'puppetserver',
}
