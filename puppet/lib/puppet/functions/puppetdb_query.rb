require 'puppet/util/puppetdb'
Puppet::Functions.create_function(:puppetdb_query) do
  dispatch :puppetdb_query do
    required_param 'Variant[String[1], Array[Data, 1]]', :query
    optional_param "Struct[{Optional['url'] => String[1], Optional['ssl_cert'] => String[1], Optional['ssl_key'] => String[1], Optional['ssl_ca'] => String[1], Optional['ssl_crl'] => String[1]}]", :options
  end

  def puppetdb_query(query, options = {})
    if options.empty?
      Puppet::Util::Puppetdb.query_puppetdb(query)
    else
      Puppet::Util::Puppetdb.query_puppetdb(query, options.transform_keys(&:to_sym))
    end
  end
end
