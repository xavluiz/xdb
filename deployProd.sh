#!/usr/bin/expect

# RSYNC the ROOT.war
spawn rsync -rave "ssh -i baddata_alpha.pem" ../baddata/target/ROOT.war ubuntu@35.160.224.213:/tmp/.
sleep 12
expect "$"

# SSH to the server
spawn ssh -i baddata_alpha.pem ubuntu@35.160.224.213
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

send "rm -rf ROOT*\r"
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
