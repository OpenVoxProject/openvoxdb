# frozen_string_literal: true

require 'open3'

module Vox
  module Shell
    RED = "\033[31m"
    GREEN = "\033[32m"
    RESET = "\033[0m"

    module_function

    def run(cmd, silent: true, print_command: false, report_status: false, allowed_exit_codes: [0])
      puts "#{GREEN}Running #{cmd}#{RESET}" if print_command
      output = ''
      Open3.popen2e(cmd) do |_stdin, stdout_stderr, thread|
        stdout_stderr.each do |line|
          puts line unless silent
          output += line
        end
        exitcode = thread.value.exitstatus
        unless allowed_exit_codes.include?(exitcode)
          err = "#{RED}Command failed! Command: #{cmd}, Exit code: #{exitcode}"
          # Print details if we were running silent
          err += "\nOutput:\n#{output}" if silent
          err += RESET
          abort err
        end
        puts "#{GREEN}Command finished with status #{exitcode}#{RESET}" if report_status
      end
      output.chomp
    end
  end
end
