#!/usr/bin/env rspec
# encoding: UTF-8

require 'spec_helper'
require 'digest/sha1'
require 'puppet/util/puppetdb'
require 'puppet/util/puppetdb/command_names'
require 'puppet/util/puppetdb/http'
require 'puppet_pal'
require 'json'


class FakeHttpResponse
  def initialize(body)
    @body = body
  end
  attr_reader :body
end

describe Puppet::Util::Puppetdb do
  subject { Object.new.extend described_class }

  describe "#submit_command" do
    let(:payload) { {'resistance' =>  'futile', 'opinion' => 'irrelevant'} }
    let(:command1) { Puppet::Util::Puppetdb::Command.new("OPEN SESAME", 1, 'foo.localdomain', Time.now.utc,
                                                         payload.merge(:uniqueprop => "command1")) }

    it "should submit the command" do
      # careful here... since we're going to stub Command.new, we need to
      # make sure we reference command1 first, because it calls Command.new.
      command1.expects(:submit).once
      Puppet::Util::Puppetdb::Command.expects(:new).once.returns(command1)
      subject.submit_command(command1.certname,
                             command1.command,
                             command1.producer_timestamp_utc,
                             command1.version) { command1.payload }
    end

  end

  describe ".query_puppetdb" do
    let(:response) { JSON.generate({'certname' => 'futile', 'status' => 'irrelevant'}) }
    let(:query) { ["=", "type", "Foo"] }
    let(:http_response) { FakeHttpResponse.new(response) }
    it "should query PuppetDB" do
      # careful here... since we're going to stub Command.new, we need to
      # make sure we reference command1 first, because it calls Command.new.
      Puppet::Util::Puppetdb::Http.expects(:action).once.returns(http_response)
      Puppet::Util::Puppetdb.query_puppetdb(query)
    end

    it "passes connection overrides to the HTTP action" do
      options = { :url => 'https://other.example:8081', :ssl_cert => '/client.pem', :ssl_key => '/client-key.pem', :ssl_ca => '/ca.pem', :ssl_crl => '/crl.pem' }
      Puppet::Util::Puppetdb::Http.expects(:action).with('/pdb/query/v4', :query, options).returns(http_response)

      expect(Puppet::Util::Puppetdb.query_puppetdb(query, options)).to eq(JSON.parse(response))
    end

    it "passes the SSL context to the HTTP post" do
      http = mock('HTTP client')
      context = stub('SSL context')
      path = URI('https://other.example:8081/pdb/query/v4')
      Puppet::Util::Puppetdb::Http.expects(:action).with('/pdb/query/v4', :query, {}).yields(http, path, context).returns(http_response)
      http.expects(:post).with do |actual_path, body, args|
        actual_path == path && JSON.parse(body) == { 'query' => query } && args[:options][:ssl_context] == context
      end.returns(http_response)

      Puppet::Util::Puppetdb.query_puppetdb(query)
    end

  end

  describe 'puppetdb_query function' do
    it 'dispatches optional overrides from a Puppet hash' do
      options = { :url => 'https://other.example:8081', :ssl_cert => '/client.pem', :ssl_key => '/key.pem', :ssl_ca => '/ca.pem', :ssl_crl => '/crl.pem' }
      Puppet::Util::Puppetdb.expects(:query_puppetdb).with('nodes {}', options).returns([])

      Puppet::Pal.in_environment('query_test', env_dir: File.expand_path('../../..', __dir__), facts: {}) do |pal|
        pal.with_script_compiler do |compiler|
          expect(compiler.evaluate_string("puppetdb_query('nodes {}', { 'url' => 'https://other.example:8081', 'ssl_cert' => '/client.pem', 'ssl_key' => '/key.pem', 'ssl_ca' => '/ca.pem', 'ssl_crl' => '/crl.pem' })")).to eq([])
        end
      end
    end

    it 'accepts a partial options hash' do
      Puppet::Util::Puppetdb.expects(:query_puppetdb).with('nodes {}', { :ssl_crl => '/crl.pem' }).returns([])

      Puppet::Pal.in_environment('query_test', env_dir: File.expand_path('../../..', __dir__), facts: {}) do |pal|
        pal.with_script_compiler do |compiler|
          expect(compiler.evaluate_string("puppetdb_query('nodes {}', { 'ssl_crl' => '/crl.pem' })")).to eq([])
        end
      end
    end

    it 'rejects unknown options and invalid values' do
      Puppet::Pal.in_environment('query_test', env_dir: File.expand_path('../../..', __dir__), facts: {}) do |pal|
        pal.with_script_compiler do |compiler|
          expect { compiler.evaluate_string("puppetdb_query('nodes {}', { 'ssl_caa' => '/ca.pem' })") }.to raise_error(Puppet::PreformattedError, /ssl_caa/)
          expect { compiler.evaluate_string("puppetdb_query('nodes {}', { 'ssl_ca' => '' })") }.to raise_error(Puppet::PreformattedError, /ssl_ca/)
        end
      end
    end

    it 'accepts a query without overrides' do
      Puppet::Util::Puppetdb.expects(:query_puppetdb).with(['from', 'nodes']).returns([])

      Puppet::Pal.in_environment('query_test', env_dir: File.expand_path('../../..', __dir__), facts: {}) do |pal|
        pal.with_script_compiler do |compiler|
          expect(compiler.evaluate_string("puppetdb_query(['from', 'nodes'])")).to eq([])
        end
      end
    end

    it 'executes a query without overrides through the real utility method' do
      http_response = FakeHttpResponse.new('[{"certname":"example.test"}]')
      Puppet::Util::Puppetdb::Http.expects(:action).with('/pdb/query/v4', :query, {}).returns(http_response)

      Puppet::Pal.in_environment('query_test', env_dir: File.expand_path('../../..', __dir__), facts: {}) do |pal|
        pal.with_script_compiler do |compiler|
          expect(compiler.evaluate_string("puppetdb_query('nodes[certname]{}')")).to eq([{ 'certname' => 'example.test' }])
        end
      end
    end

    it 'executes a query with overrides through the real utility method' do
      http_response = FakeHttpResponse.new('[]')
      Puppet::Util::Puppetdb::Http.expects(:action).with('/pdb/query/v4', :query, { :ssl_crl => '/crl.pem' }).returns(http_response)

      Puppet::Pal.in_environment('query_test', env_dir: File.expand_path('../../..', __dir__), facts: {}) do |pal|
        pal.with_script_compiler do |compiler|
          expect(compiler.evaluate_string("puppetdb_query('nodes[certname]{}', { 'ssl_crl' => '/crl.pem' })")).to eq([])
        end
      end
    end
  end

end
