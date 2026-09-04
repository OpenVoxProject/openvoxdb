#!/usr/bin/env ruby
# frozen_string_literal: true

file = ARGV[0] || "project.clj"
text = File.read(file)

v = text[/^\(defproject\s+\S+\s+"([^"]+)"/m, 1]
abort("Couldn't find defproject version string in #{file}") unless v

re = /\[org\.openvoxproject\/puppetdb\s+"[^"]+"\]/
abort("Couldn't find literal [org.openvoxproject/puppetdb \"...\"] in #{file}") unless text.match?(re)

text.gsub!(re, %[[org.openvoxproject/puppetdb "#{v}"]])
File.write(file, text)

puts "Synced ezbake dep to #{v}"
