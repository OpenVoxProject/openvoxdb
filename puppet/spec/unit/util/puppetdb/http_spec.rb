require 'spec_helper'
require 'puppet/util/puppetdb/http'
require 'puppet/ssl/ssl_provider'

describe Puppet::Util::Puppetdb::Http do

  if Gem::Version.new(Puppet.version) < Gem::Version.new("6.4.0")
    http_expects = :http_instance
  else
    http_expects = :connection
  end

  describe "#concat_url_snippets" do
    it "should avoid a double slash" do
      described_class.concat_url_snippets('/foo/', '/bar/').should == '/foo/bar/'
    end
    it "should add a slash if needed" do
      described_class.concat_url_snippets('/foo', 'bar/').should == '/foo/bar/'
      described_class.concat_url_snippets('/foo', 'bar/').should == '/foo/bar/'
    end
    it "should do the right thing if only one snippet has a slash" do
      described_class.concat_url_snippets('/foo', '/bar/').should == '/foo/bar/'
      described_class.concat_url_snippets('/foo/', 'bar/').should == '/foo/bar/'
    end
  end

  before :each do
      Puppet::Util::Puppetdb.stubs(:config).returns config
      described_class.reset_query_failover()
      described_class.class_variable_set(:@@submit_only_url_timeouts, {})
  end

  let(:config) do
    config = stub 'config'
    config.stubs(:server_urls).returns [URI("https://server1:8080/foo"), URI("https://server2:8181/bar")]
    config.stubs(:server_url_timeout).returns 30
    config.stubs(:server_url_config?).returns true
    config.stubs(:sticky_read_failover).returns false
    config.stubs(:command_broadcast).returns false
    config.stubs(:min_successful_submissions).returns 1
    config.stubs(:submit_only_server_urls).returns []
    config.stubs(:verify_client_certificate).returns true
    config
  end

  let(:http1) {stub 'http'}
  let(:http2) {stub 'http'}
  let(:http3) {stub 'http'}
  let(:url) { "mock url" }
  let(:nethttpok) { Net::HTTPOK.new('1.1', 200, 'OK') }
  let(:responseok) { create_http_response(url, nethttpok) }

  describe "#action" do
    describe "for request_type=:query" do
      it "uses the overridden URL without changing the configured URLs" do
        Puppet::HTTP::Client.expects(:new).returns(http1)
        http1.expects(:get).with do |uri, opts|
          uri.to_s == 'https://other.example:8081/pdb/query/v4'
        end.returns(responseok)

        described_class.action('/pdb/query/v4', :query, :url => 'https://other.example:8081') do |http_instance, path|
          http_instance.get(path, {})
        end

        expect(config.server_urls.map(&:to_s)).to eq(['https://server1:8080/foo', 'https://server2:8181/bar'])
      end

      it "rejects an insecure URL override" do
        expect {
          described_class.action('/pdb/query/v4', :query, :url => 'http://other.example:8081') {}
        }.to raise_error(/must be https/)
      end

      it "reports the overridden URL when the request fails" do
        Puppet::HTTP::Client.expects(:new).returns(http1)
        http1.expects(:get).raises(Puppet::HTTP::HTTPError, 'Connection refused')

        expect {
          described_class.action('/pdb/query/v4', :query, :url => 'https://other.example:8081') do |http_instance, path|
            http_instance.get(path, {})
          end
        }.to raise_error(Puppet::Error, /https:\/\/other\.example:8081/)
      end

      it "uses the overridden TLS paths for the client context" do
        cert_provider = mock('certificate provider')
        ssl_provider = mock('SSL provider')
        ssl_context = stub('SSL context')
        certname = Puppet[:certname]
        options = { :ssl_cert => '/client.pem', :ssl_key => '/client-key.pem', :ssl_ca => '/ca.pem' }
        Puppet::X509::CertProvider.expects(:new).with(capath: '/ca.pem', hostcert: '/client.pem', hostprivkey: '/client-key.pem').returns(cert_provider)
        cert_provider.expects(:load_cacerts).with(required: true).returns([:ca])
        cert_provider.expects(:load_private_key).with(Puppet[:certname], required: true).returns(:key)
        cert_provider.expects(:load_client_cert).with(Puppet[:certname], required: true).returns(:cert)
        Puppet.stubs(:[]).with(:certificate_revocation).returns(false)
        Puppet.stubs(:[]).with(:certname).returns(certname)
        Puppet::SSL::SSLProvider.expects(:new).returns(ssl_provider)
        ssl_provider.expects(:create_context).with(cacerts: [:ca], crls: [], private_key: :key, client_cert: :cert, revocation: false).returns(ssl_context)
        Puppet::HTTP::Client.expects(:new).returns(http1)
        http1.expects(:get).with { |uri, opts| opts[:ssl_context] == ssl_context }.returns(responseok)

        described_class.action('/pdb/query/v4', :query, options) do |http_instance, path, context|
          http_instance.get(path, :ssl_context => context)
        end
      end

      it "uses the overridden CRL with default certificate paths" do
        cert_provider = mock('certificate provider')
        ssl_provider = mock('SSL provider')
        certname = Puppet[:certname]
        Puppet::X509::CertProvider.expects(:new).with(crlpath: '/crl.pem').returns(cert_provider)
        cert_provider.expects(:load_cacerts).with(required: true).returns([:ca])
        cert_provider.expects(:load_crls).with(required: true).returns([:crl])
        cert_provider.expects(:load_private_key).with(certname, required: true).returns(:key)
        cert_provider.expects(:load_client_cert).with(certname, required: true).returns(:cert)
        Puppet.stubs(:[]).with(:certificate_revocation).returns(:chain)
        Puppet.stubs(:[]).with(:certname).returns(certname)
        Puppet::SSL::SSLProvider.expects(:new).returns(ssl_provider)
        ssl_provider.expects(:create_context).with(cacerts: [:ca], crls: [:crl], private_key: :key, client_cert: :cert, revocation: :chain).returns(:context)
        Puppet::HTTP::Client.expects(:new).returns(http1)
        http1.expects(:get).with { |uri, opts| opts[:ssl_context] == :context }.returns(responseok)

        described_class.action('/pdb/query/v4', :query, :ssl_crl => '/crl.pem') do |http_instance, path, context|
          http_instance.get(path, :ssl_context => context)
        end
      end

      it "uses the overridden CRL for leaf revocation checking without a client certificate" do
        config.stubs(:verify_client_certificate).returns(false)
        cert_provider = mock('certificate provider')
        ssl_provider = mock('SSL provider')
        Puppet::X509::CertProvider.expects(:new).with(crlpath: '/crl.pem').returns(cert_provider)
        cert_provider.expects(:load_cacerts).with(required: true).returns([:ca])
        cert_provider.expects(:load_crls).with(required: true).returns([:crl])
        Puppet.stubs(:[]).with(:certificate_revocation).returns(:leaf)
        Puppet::SSL::SSLProvider.expects(:new).returns(ssl_provider)
        ssl_provider.expects(:create_root_context).with(cacerts: [:ca], crls: [:crl], revocation: :leaf).returns(:context)
        Puppet::HTTP::Client.expects(:new).returns(http1)
        http1.expects(:get).with { |uri, opts| opts[:ssl_context] == :context }.returns(responseok)

        described_class.action('/pdb/query/v4', :query, :ssl_crl => '/crl.pem') do |http_instance, path, context|
          http_instance.get(path, :ssl_context => context)
        end
      end

      it "does not load the overridden CRL when revocation checking is disabled" do
        config.stubs(:verify_client_certificate).returns(false)
        cert_provider = mock('certificate provider')
        ssl_provider = mock('SSL provider')
        Puppet::X509::CertProvider.expects(:new).with(crlpath: '/crl.pem').returns(cert_provider)
        cert_provider.expects(:load_cacerts).with(required: true).returns([:ca])
        cert_provider.expects(:load_crls).never
        Puppet.stubs(:[]).with(:certificate_revocation).returns(false)
        Puppet::SSL::SSLProvider.expects(:new).returns(ssl_provider)
        ssl_provider.expects(:create_root_context).with(cacerts: [:ca], crls: [], revocation: false).returns(:context)
        Puppet::HTTP::Client.expects(:new).returns(http1)
        http1.expects(:get).with { |uri, opts| opts[:ssl_context] == :context }.returns(responseok)

        described_class.action('/pdb/query/v4', :query, :ssl_crl => '/crl.pem') do |http_instance, path, context|
          http_instance.get(path, :ssl_context => context)
        end
      end

      it "uses default certificate paths for omitted TLS overrides" do
        cert_provider = mock('certificate provider')
        ssl_provider = mock('SSL provider')
        certname = Puppet[:certname]
        Puppet::X509::CertProvider.expects(:new).with(capath: '/ca.pem').returns(cert_provider)
        cert_provider.expects(:load_cacerts).with(required: true).returns([:ca])
        cert_provider.expects(:load_crls).with(required: true).returns([:crl])
        cert_provider.expects(:load_private_key).with(Puppet[:certname], required: true).returns(:key)
        cert_provider.expects(:load_client_cert).with(Puppet[:certname], required: true).returns(:cert)
        Puppet.stubs(:[]).with(:certificate_revocation).returns(:chain)
        Puppet.stubs(:[]).with(:certname).returns(certname)
        Puppet::SSL::SSLProvider.expects(:new).returns(ssl_provider)
        ssl_provider.expects(:create_context).with(cacerts: [:ca], crls: [:crl], private_key: :key,
                                                   client_cert: :cert, revocation: :chain).returns(:context)
        Puppet::HTTP::Client.expects(:new).returns(http1)
        http1.expects(:get).with { |uri, opts| opts[:ssl_context] == :context }.returns(responseok)

        described_class.action('/pdb/query/v4', :query, :ssl_ca => '/ca.pem') do |http_instance, path, context|
          http_instance.get(path, :ssl_context => context)
        end
      end

      it "uses a root-only context for a CA override when client certificates are disabled" do
        config.stubs(:verify_client_certificate).returns(false)
        cert_provider = mock('certificate provider')
        ssl_provider = mock('SSL provider')
        Puppet::X509::CertProvider.expects(:new).with(capath: '/ca.pem').returns(cert_provider)
        cert_provider.expects(:load_cacerts).with(required: true).returns([:ca])
        Puppet.stubs(:[]).with(:certificate_revocation).returns(false)
        Puppet::SSL::SSLProvider.expects(:new).returns(ssl_provider)
        ssl_provider.expects(:create_root_context).with(cacerts: [:ca], crls: [], revocation: false).returns(:context)
        Puppet::HTTP::Client.expects(:new).returns(http1)
        http1.expects(:get).with { |uri, opts| opts[:ssl_context] == :context }.returns(responseok)

        described_class.action('/pdb/query/v4', :query, :ssl_ca => '/ca.pem') do |http_instance, path, context|
          http_instance.get(path, :ssl_context => context)
        end
      end

      it "uses explicit client credentials when client certificates are disabled" do
        config.stubs(:verify_client_certificate).returns(false)
        cert_provider = mock('certificate provider')
        ssl_provider = mock('SSL provider')
        certname = Puppet[:certname]
        Puppet::X509::CertProvider.expects(:new).with(hostcert: '/client.pem', hostprivkey: '/key.pem').returns(cert_provider)
        cert_provider.expects(:load_cacerts).with(required: true).returns([:ca])
        cert_provider.expects(:load_private_key).with(certname, required: true).returns(:key)
        cert_provider.expects(:load_client_cert).with(certname, required: true).returns(:cert)
        Puppet.stubs(:[]).with(:certificate_revocation).returns(false)
        Puppet.stubs(:[]).with(:certname).returns(certname)
        Puppet::SSL::SSLProvider.expects(:new).returns(ssl_provider)
        ssl_provider.expects(:create_context).with(cacerts: [:ca], crls: [], private_key: :key,
                                                   client_cert: :cert, revocation: false).returns(:context)
        Puppet::HTTP::Client.expects(:new).returns(http1)
        http1.expects(:get).with { |uri, opts| opts[:ssl_context] == :context }.returns(responseok)

        described_class.action('/pdb/query/v4', :query, :ssl_cert => '/client.pem', :ssl_key => '/key.pem') do |http_instance, path, context|
          http_instance.get(path, :ssl_context => context)
        end
      end

      it "call the proc argument with a correct path" do
        Puppet::HTTP::Client.expects(:new).returns(http1)
        http1.expects(:get).with do |uri, opts|
          "/foo/bar/baz" == uri.path
        end.returns responseok

         described_class.action("/bar/baz", :query) do |http_instance, path|
           http_instance.get(path, {})
         end
       end

       it "should not fail over when the first url works" do
         Puppet::HTTP::Client.expects(:new).returns(http1).once

         http1.expects(:get).with do |uri, opts|
           "/foo/baz" == uri.path
         end.returns responseok

         response = described_class.action("/baz", :query) do |http_instance, path|
           http_instance.get(path, {})
         end

         response.code.should == 200
         response.reason.should == 'OK'
       end

       it "fails over to the next url when it can't connect" do
         Puppet::HTTP::Client.expects(:new).returns(http1)

         http1.expects(:get).with do |uri, opts|
           "/foo/baz" == uri.path
         end.raises Puppet::HTTP::HTTPError, "Connection refused"

         http1.expects(:get).with do |uri, opts|
           "/bar/baz" == uri.path
         end.returns responseok

         response = described_class.action("/baz", :query) do |http_instance, path|
           http_instance.get(path, {})
         end

         response.code.should == 200
         response.reason.should == 'OK'
       end

       it "fails over to the next url on a server error" do
         Puppet::HTTP::Client.expects(:new).returns(http1)

         http1.expects(:get).with do |uri, opts|
           "/foo/baz" == uri.path
         end.raises Puppet::HTTP::ResponseError, create_http_response(url, Net::HTTPServiceUnavailable.new('1.1', 503, "Unavailable"))

         http1.expects(:get).with do |uri, opts|
           "/bar/baz" == uri.path
         end.returns responseok

         response = described_class.action("/baz", :query) do |http_instance, path|
           http_instance.get(path, {})
         end

         response.code.should == 200
         response.reason.should == 'OK'
       end

       it "raises an exception when all urls fail" do
         Puppet::HTTP::Client.expects(:new).returns(http1)

         http1.expects(:get).with do |uri, opts|
           "/foo/baz" == uri.path
         end.raises Puppet::HTTP::ResponseError, create_http_response(url, Net::HTTPServiceUnavailable.new('1.1', 503, "Unavailable"))
         http1.expects(:get).with do |uri, opts|
           "/bar/baz" == uri.path
         end.raises Puppet::HTTP::ResponseError, create_http_response(url, Net::HTTPServiceUnavailable.new('1.1', 503, "Unavailable"))

         expect {
            described_class.action("/baz", :query) do |http_instance, path|
              http_instance.get(path, {})
            end
         }.to raise_error Puppet::Error, /Failed to execute/
       end

       it "fails over after IOError" do
         Puppet::HTTP::Client.expects(:new).returns(http1)

         http1.expects(:get).with do |uri, opts|
           "/foo/baz" == uri.path
         end.raises Puppet::HTTP::HTTPError, "IO Error"
         http1.expects(:get).with do |uri, opts|
           "/bar/baz" == uri.path
         end.returns responseok

         response = described_class.action("/baz", :query) do |http_instance, path|
           http_instance.get(path, {})
         end

         response.code.should == 200
         response.reason.should == 'OK'
       end

       it "times out and rolls to the next url" do
         Puppet::HTTP::Client.expects(:new).returns(http1)

         http1.expects(:get).with do |uri, opts|
           "/foo/baz" == uri.path
         end.raises Timeout::Error
         http1.expects(:get).with do |uri, opts|
           "/bar/baz" == uri.path
         end.returns responseok

         response = described_class.action("/baz", :query) do |http_instance, path|
           http_instance.get(path, {})
         end

         response.code.should == 200
         response.reason.should == 'OK'
       end

       it "doesn't send queries to hosts in submit_only_server_urls" do
         Puppet::HTTP::Client.expects(:new).returns(http1)

         http1.expects(:get).with do |uri, opts|
           "/foo/baz" == uri.path
         end.raises Puppet::HTTP::HTTPError, "Connection refused"
         http1.expects(:get).with do |uri, opts|
           "/bar/baz" == uri.path
         end.returns responseok

         config.stubs(:submit_only_server_urls).returns [URI("https://server3:8282/qux")]

         response = described_class.action("/baz", :query) do |http_instance, path|
           http_instance.get(path, {})
         end

         response.code.should == 200
         response.reason.should == 'OK'
       end

       describe "when sticky_read_failover is false" do
         it "retries the first host after failover" do
           Puppet::HTTP::Client.expects(:new).returns(http1)

           http1.expects(:get).with do |uri, opts|
             "/foo/baz" == uri.path
           end.raises(Puppet::HTTP::ResponseError, create_http_response(url, Net::HTTPServiceUnavailable.new('1.1', 503, "Unavailable"))).twice

           http1.expects(:get).with do |uri, opts|
             "/bar/baz" == uri.path
           end.returns(responseok).twice

           response = described_class.action("/baz", :query) do |http_instance, path|
             http_instance.get(path, {})
           end

           response.code.should == 200
           response.reason.should == 'OK'

           response = described_class.action("/baz", :query) do |http_instance, path|
             http_instance.get(path, {})
           end

           response.code.should == 200
           response.reason.should == 'OK'
         end
       end

       describe "when sticky_read_failover is true" do
        before :each do
          config.stubs(:sticky_read_failover).returns(true)
        end

         it "reuses the same host after failover" do
           Puppet::HTTP::Client.expects(:new).returns(http1)

           http1.expects(:get).with do |uri, opts|
             "/foo/baz" == uri.path
           end.raises(Puppet::HTTP::ResponseError, create_http_response(url, Net::HTTPServiceUnavailable.new('1.1', 503, "Unavailable"))).once
           http1.expects(:get).with do |uri, opts|
             "/bar/baz" == uri.path
           end.returns(responseok).twice

           response = described_class.action("/baz", :query) do |http_instance, path|
             http_instance.get(path, {})
           end

           response.code.should == 200
           response.reason.should == 'OK'

           response = described_class.action("/baz", :query) do |http_instance, path|
             http_instance.get(path, {})
           end

           response.code.should == 200
           response.reason.should == 'OK'
         end
       end

    end

    describe "for request_type=:command" do
      describe "when command_broadcast is true" do
        before :each do
          config.stubs(:command_broadcast).returns(true)
        end

        it "should try to post to all URLs" do
          Puppet::HTTP::Client.expects(:new).returns(http1)

          http1.expects(:post).with do |uri, opts|
            "/foo/baz" == uri.path
          end.returns responseok
          http1.expects(:post).with do |uri, opts|
            "/bar/baz" == uri.path
          end.returns responseok

          response = described_class.action("/baz", :command) do |http_instance, path|
            http_instance.post(path, {})
          end

          response.code.should == 200
          response.reason.should == 'OK'
        end

        it "fails over to the next url when it can't connect" do
          Puppet::HTTP::Client.expects(:new).returns(http1)

          http1.expects(:post).with do |uri, opts|
            "/foo/baz" == uri.path
          end.raises Puppet::HTTP::HTTPError, "Connection refused"
          http1.expects(:post).with do |uri, opts|
            "/bar/baz" == uri.path
          end.returns responseok

          response = described_class.action("/baz", :command) do |http_instance, path|
            http_instance.post(path, {})
          end

          response.code.should == 200
          response.reason.should == 'OK'
        end

        it "fails over to the next url when one service is unavailable" do
          Puppet::HTTP::Client.expects(:new).returns(http1)

          http1.expects(:post).with do |uri, opts|
            "/foo/baz" == uri.path
          end.returns(responseok)
          http1.expects(:post).with do |uri, opts|
            "/bar/baz" == uri.path
          end.raises Puppet::HTTP::ResponseError, create_http_response(url, Net::HTTPServiceUnavailable.new('1.1', 503, "Unavailable"))

          response = described_class.action("/baz", :command) do |http_instance, path|
            http_instance.post(path, {})
          end

          response.code.should == 200
          response.reason.should == 'OK'
        end

        it "raises an exception when all urls fail" do
          Puppet::HTTP::Client.expects(:new).returns(http1)

          http1.expects(:post).with do |uri, opts|
            "/foo/baz" == uri.path
          end.raises Puppet::HTTP::ResponseError, create_http_response(url, Net::HTTPServiceUnavailable.new('1.1', 503, "Unavailable"))
          http1.expects(:post).with do |uri, opts|
            "/bar/baz" == uri.path
          end.raises Puppet::HTTP::ResponseError, create_http_response(url, Net::HTTPServiceUnavailable.new('1.1', 503, "Unavailable"))

          expect {
             described_class.action("/baz", :command) do |http_instance, path|
               http_instance.post(path, {})
             end
          }.to raise_error Puppet::Error, /Failed to execute/
        end

        it "raises an exception when min_successful_submissions is not met" do
          config.stubs(:min_successful_submissions).returns 2

          Puppet::HTTP::Client.expects(:new).returns(http1)

          http1.expects(:post).with do |uri, opts|
            "/foo/baz" == uri.path
          end.raises Puppet::HTTP::HTTPError, "Connection refused"
          http1.expects(:post).with do |uri, opts|
            "/bar/baz" == uri.path
          end.returns responseok

          expect {
             described_class.action("/baz", :command) do |http_instance, path|
               http_instance.post(path, {})
             end
          }.to raise_error Puppet::Error, /Failed to execute/
        end

        it "works when min_successful_submissions is met" do
          config.stubs(:min_successful_submissions).returns 2

          Puppet::HTTP::Client.expects(:new).returns(http1)

          http1.expects(:post).with do |uri, opts|
            "/foo/baz" == uri.path
          end.returns responseok
          http1.expects(:post).with do |uri, opts|
            "/bar/baz" == uri.path
          end.returns responseok

          response = described_class.action("/baz", :command) do |http_instance, path|
            http_instance.post(path, {})
          end

          response.code.should == 200
          response.reason.should == 'OK'
        end

        it "sends commands to hosts in submit_only_server_urls" do
          config.stubs(:submit_only_server_urls).returns [URI("https://server3:8282/qux")]

          Puppet::HTTP::Client.expects(:new).returns(http1)

          http1.expects(:post).with do |uri, opts|
            "/foo/baz" == uri.path
          end.returns responseok
          http1.expects(:post).with do |uri, opts|
            "/bar/baz" == uri.path
          end.returns responseok
          http1.expects(:post).with.with do |uri, opts|
            "/qux/baz" == uri.path
          end.returns responseok

          response = described_class.action("/baz", :command) do |http_instance, path|
            http_instance.post(path, {})
          end

          response.code.should == 200
          response.reason.should == 'OK'
        end

        context "tracking timeouts for `submit_only` urls" do
          it "tracks timeouts for `submit_only` urls" do
            submit_only_url = URI("https://server3:8282/qux")
            config.stubs(:submit_only_server_urls).returns [submit_only_url]

            Puppet::HTTP::Client.expects(:new).returns(http1)

            http1.expects(:post).with do |uri, opts|
              "/foo/baz" == uri.path
            end.times(3).returns responseok
            http1.expects(:post).with do |uri, opts|
              "/bar/baz" == uri.path
            end.times(3).returns responseok
            http1.expects(:post).with.with do |uri, opts|
              "/qux/baz" == uri.path
            end.twice.raises Timeout::Error

            # First request with timing out submit_only URL
            response = described_class.action("/baz", :command) do |http_instance, path|
              http_instance.post(path, {})
            end

            described_class.class_variable_get(:@@submit_only_url_timeouts)[submit_only_url].should == true
            # Url has not been removed yet, even though it has timed out once
            config.submit_only_server_urls.should == [submit_only_url]

            response.code.should == 200
            response.reason.should == 'OK'

            # Second request with timing out submit_only URL
            response = described_class.action("/baz", :command) do |http_instance, path|
              http_instance.post(path, {})
            end

            described_class.class_variable_get(:@@submit_only_url_timeouts)[submit_only_url].should == false
            # Timed out twice, so now the URL is removed so we don't keep trying it
            config.submit_only_server_urls.should == []

            response.code.should == 200
            response.reason.should == 'OK'

            # Third request with `submit_only` url removed
            response = described_class.action("/baz", :command) do |http_instance, path|
              http_instance.post(path, {})
            end

            response.code.should == 200
            response.reason.should == 'OK'
          end

          it "does not remove the url if it only times out once" do
            submit_only_url = URI("https://server3:8282/qux")
            config.stubs(:submit_only_server_urls).returns [submit_only_url]

            Puppet::HTTP::Client.expects(:new).returns(http1)

            http1.expects(:post).with do |uri, opts|
              "/foo/baz" == uri.path
            end.twice.returns responseok
            http1.expects(:post).with do |uri, opts|
              "/bar/baz" == uri.path
            end.twice.returns responseok
            http1.expects(:post).with.with do |uri, opts|
              "/qux/baz" == uri.path
            end.twice.raises(Timeout::Error).then.returns responseok

            # First request with timing out submit_only URL
            response = described_class.action("/baz", :command) do |http_instance, path|
              http_instance.post(path, {})
            end

            described_class.class_variable_get(:@@submit_only_url_timeouts)[submit_only_url].should == true
            # Url has not been removed yet, even though it has timed out once
            config.submit_only_server_urls.should == [submit_only_url]

            response.code.should == 200
            response.reason.should == 'OK'

            # Second request with timing out submit_only URL
            response = described_class.action("/baz", :command) do |http_instance, path|
              http_instance.post(path, {})
            end

            described_class.class_variable_get(:@@submit_only_url_timeouts)[submit_only_url].should == false
            # Timed out twice, so now the URL is removed so we don't keep trying it
            config.submit_only_server_urls.should == [submit_only_url]

            response.code.should == 200
            response.reason.should == 'OK'
          end

          it "does not track timeouts for non-submit_only urls" do
            submit_only_url = URI("https://server3:8282/qux")
            config.stubs(:submit_only_server_urls).returns [submit_only_url]

            Puppet::HTTP::Client.expects(:new).returns(http1)

            http1.expects(:post).with do |uri, opts|
              "/foo/baz" == uri.path
            end.returns responseok
            http1.expects(:post).with do |uri, opts|
              "/bar/baz" == uri.path
            end.raises Timeout::Error
            http1.expects(:post).with.with do |uri, opts|
              "/qux/baz" == uri.path
            end.returns responseok

            response = described_class.action("/baz", :command) do |http_instance, path|
              http_instance.post(path, {})
            end

            # We set the submit_only url entry to false (one entry), and do nothing for the regular URL
            described_class.class_variable_get(:@@submit_only_url_timeouts)[submit_only_url].should == false
            described_class.class_variable_get(:@@submit_only_url_timeouts).count.should == 1

            response.code.should == 200
            response.reason.should == 'OK'
          end
        end
      end

      describe "when command_broadcast is false" do
        before :each do
          config.stubs(:command_broadcast).returns(false)
        end

        it "should try to post to only the first URL if it succeeds" do
          Puppet::HTTP::Client.expects(:new).returns(http1)

          http1.expects(:post).with do |uri, opts|
            "/foo/baz" == uri.path
          end.returns responseok
          http1.expects(:post).with do |uri, opts|
            "/bar/baz" == uri.path
          end.never

          response = described_class.action("/baz", :command) do |http_instance, path|
            http_instance.post(path, {})
          end

          response.code.should == 200
          response.reason.should == 'OK'
        end

        it "fails over to the next url when it can't connect" do
          Puppet::HTTP::Client.expects(:new).returns(http1)

          http1.expects(:post).with do |uri, opts|
            "/foo/baz" == uri.path
          end.raises Puppet::HTTP::HTTPError, "Connection refused"
          http1.expects(:post).with do |uri, opts|
            "/bar/baz" == uri.path
          end.returns responseok

          response = described_class.action("/baz", :command) do |http_instance, path|
            http_instance.post(path, {})
          end

          response.code.should == 200
          response.reason.should == 'OK'
        end

        it "raises an exception when all urls fail" do
          Puppet::HTTP::Client.expects(:new).returns(http1)

          http1.expects(:post).with do |uri, opts|
            "/foo/baz" == uri.path
          end.raises Puppet::HTTP::ResponseError, create_http_response(url, Net::HTTPServiceUnavailable.new('1.1', 503, "Unavailable"))
          http1.expects(:post).with do |uri, opts|
            "/bar/baz" == uri.path
          end.raises Puppet::HTTP::ResponseError, create_http_response(url, Net::HTTPServiceUnavailable.new('1.1', 503, "Unavailable"))

          expect {
             described_class.action("/baz", :command) do |http_instance, path|
               http_instance.post(path, {})
             end
          }.to raise_error Puppet::Error, /Failed to execute/
        end

        it "sends commands to hosts in submit_only_server_urls" do
          config.stubs(:submit_only_server_urls).returns [URI("https://server3:8282/qux")]

          Puppet::HTTP::Client.expects(:new).returns(http1)

          http1.expects(:post).with do |uri, opts|
            "/foo/baz" == uri.path
          end.raises Puppet::HTTP::ResponseError, create_http_response(url, Net::HTTPServiceUnavailable.new('1.1', 503, "Unavailable"))
          http1.expects(:post).with do |uri, opts|
            "/bar/baz" == uri.path
          end.raises Puppet::HTTP::ResponseError, create_http_response(url, Net::HTTPServiceUnavailable.new('1.1', 503, "Unavailable"))
          http1.expects(:post).with do |uri, opts|
            "/qux/baz" == uri.path
          end.returns responseok

          response = described_class.action("/baz", :command) do |http_instance, path|
            http_instance.post(path, {})
          end

          response.code.should == 200
          response.reason.should == 'OK'
        end

        context "tracking timeouts for `submit_only` urls" do
          it "tracks timeouts for `submit_only` urls" do
            submit_only_url = URI("https://server3:8282/qux")
            config.stubs(:submit_only_server_urls).returns [submit_only_url]

            Puppet::HTTP::Client.expects(:new).returns(http1)

            http1.expects(:post).with do |uri, opts|
              "/foo/baz" == uri.path
            end.times(3).raises Puppet::HTTP::ResponseError, create_http_response(url, Net::HTTPServiceUnavailable.new('1.1', 503, "Unavailable"))
            http1.expects(:post).with do |uri, opts|
              "/bar/baz" == uri.path
            end.times(3).raises Puppet::HTTP::ResponseError, create_http_response(url, Net::HTTPServiceUnavailable.new('1.1', 503, "Unavailable"))
            http1.expects(:post).with.with do |uri, opts|
              "/qux/baz" == uri.path
            end.twice.raises Timeout::Error

            # First request with timing out submit_only url
            # All requests fail here, so the action will raise
            expect {
              described_class.action("/baz", :command) do |http_instance, path|
                http_instance.post(path, {})
              end
            }.to raise_error(Puppet::Error)

            described_class.class_variable_get(:@@submit_only_url_timeouts)[submit_only_url].should == true
            # Url has not been removed yet, even though it has timed out once
            config.submit_only_server_urls.should == [submit_only_url]

            # Second request with timing out submit_only url
            # All requests fail here, so the action will raise
            expect {
              described_class.action("/baz", :command) do |http_instance, path|
                http_instance.post(path, {})
              end
            }.to raise_error(Puppet::Error)

            described_class.class_variable_get(:@@submit_only_url_timeouts)[submit_only_url].should == false
            # Timed out twice, so now the URL is removed so we don't keep trying it
            config.submit_only_server_urls.should == []

            # Third request with `submit_only` url removed
            # All requests fail here, so the action will raise
            expect {
              described_class.action("/baz", :command) do |http_instance, path|
                http_instance.post(path, {})
              end
            }.to raise_error(Puppet::Error)
          end

          it "does not remove the url if it only times out once" do
            submit_only_url = URI("https://server3:8282/qux")
            config.stubs(:submit_only_server_urls).returns [submit_only_url]

            Puppet::HTTP::Client.expects(:new).returns(http1)

            http1.expects(:post).with do |uri, opts|
              "/foo/baz" == uri.path
            end.twice.raises Puppet::HTTP::ResponseError, create_http_response(url, Net::HTTPServiceUnavailable.new('1.1', 503, "Unavailable"))
            http1.expects(:post).with do |uri, opts|
              "/bar/baz" == uri.path
            end.twice.raises Puppet::HTTP::ResponseError, create_http_response(url, Net::HTTPServiceUnavailable.new('1.1', 503, "Unavailable"))
            http1.expects(:post).with.with do |uri, opts|
              "/qux/baz" == uri.path
            end.twice.raises(Timeout::Error).then.returns(responseok)

            # First request with timing out submit_only url
            # All requests fail here, so the action will raise
            expect {
              described_class.action("/baz", :command) do |http_instance, path|
                http_instance.post(path, {})
              end
            }.to raise_error(Puppet::Error)

            described_class.class_variable_get(:@@submit_only_url_timeouts)[submit_only_url].should == true
            # Url has not been removed yet, even though it has timed out once
            config.submit_only_server_urls.should == [submit_only_url]

            # Second request with succeeding submit_only url
            response = described_class.action("/baz", :command) do |http_instance, path|
              http_instance.post(path, {})
            end

            described_class.class_variable_get(:@@submit_only_url_timeouts)[submit_only_url].should == false
            # Did not time out again, so we do not remove the url
            config.submit_only_server_urls.should == [submit_only_url]
            response.code.should == 200
            response.reason.should == 'OK'
          end

          it "does not track timeouts for non-submit_only urls" do
            submit_only_url = URI("https://server3:8282/qux")
            config.stubs(:submit_only_server_urls).returns [submit_only_url]

            Puppet::HTTP::Client.expects(:new).returns(http1)

            http1.expects(:post).with do |uri, opts|
              "/foo/baz" == uri.path
            end.raises Puppet::HTTP::ResponseError, create_http_response(url, Net::HTTPServiceUnavailable.new('1.1', 503, "Unavailable"))
            http1.expects(:post).with do |uri, opts|
              "/bar/baz" == uri.path
            end.raises Timeout::Error
            http1.expects(:post).with.with do |uri, opts|
              "/qux/baz" == uri.path
            end.returns responseok

            response = described_class.action("/baz", :command) do |http_instance, path|
              http_instance.post(path, {})
            end

            # We set the submit_only url entry to false (one entry), and do nothing for the regular URL
            described_class.class_variable_get(:@@submit_only_url_timeouts)[submit_only_url].should == false
            described_class.class_variable_get(:@@submit_only_url_timeouts).count.should == 1

            response.code.should == 200
            response.reason.should == 'OK'
          end
        end
      end

    end
  end
end
