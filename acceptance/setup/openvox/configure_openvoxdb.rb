# This mirrors the structure of
# setup/pre_suite/90_install_devel_puppetdb.rb, but just
# configures rather than trying to install openvox packages.
test_name('configure openvoxdb using the puppetdb module') do
  databases.each do
    setup_openvoxdb_certs(database)
    configure_postgresql_repos_on_el(database)
    configure_openvoxdb(database)

    # The package should automatically start the service on debian.
    # On redhat, it doesn't. However, during test runs where we're
    # doing package upgrades, the redhat package *should* detect that
    # the service was running before the upgrade, and it should restart
    # it automatically.
    #
    # That leaves the case where we're on a redhat box and we're
    # running the tests as :install only (as opposed to :upgrade).
    # In that case we need to start the service ourselves here.
    os = test_config[:os_families][database.name]
    if test_config[:install_mode] == :install and [:redhat].include?(os)
      start_puppetdb(database)
    else
      # make sure it got started by the package install/upgrade
      sleep_until_started(database)
    end
  end

  configure_openvoxdb_termini(master, databases)
end
