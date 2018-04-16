#!/usr/bin/expect

#
# This script should be run from within the "baddata_deploy" directory
#

# RSYNC the ROOT.war
# spawn rsync target/ROOT.war baddata@192.169.177.16:/tmp/.
spawn rsync -rave "ssh -i baddata_alpha.pem" ../baddata/target/ROOT.war ubuntu@34.209.0.235:/tmp/.
# expect "password:*"
# send "BaddataIO1!\r"
sleep 12
expect "$"

# SSH to the server
# spawn ssh baddata@192.169.177.16
spawn ssh -i baddata_alpha.pem ubuntu@34.209.0.235
# expect "password:*"
# send "BaddataIO1!\r"
sleep 2
expect "$"

# PERFORM commands
send "sudo -i\r"
expect "*#"

send "cd \/opt\/tomcat8\/webapps\r"
expect "\/opt\/tomcat8\/webapps#"
sleep 1

send "systemctl stop tomcat\r"
expect "\/opt\/tomcat8\/webapps#"
sleep 10

send "rm -rf ROOT.war ROOT\r"
expect "\/opt\/tomcat8\/webapps#"
sleep 1

send "rm -rf ..\/logs\/*\r"
expect "\/opt\/tomcat8\/webapps#"
sleep 1

send "cp \/tmp\/ROOT.war .\r"
expect "\/opt\/tomcat8\/webapps#"
sleep 1

send "systemctl start tomcat\r"
expect "\/opt\/tomcat8\/webapps#"
sleep 5

send "exit\r"
sleep 1

send "exit\r"

# DONE
expect eof
