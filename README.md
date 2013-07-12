[Repository](https://github.com/pallet/pallet-repl) &#xb7;
[Issues](https://github.com/pallet/pallet-repl/issues) &#xb7;
[API docs](http://palletops.com/pallet-repl/0.8/api) &#xb7;
[Annotated source](http://palletops.com/pallet-repl/0.8/annotated/uberdoc.html) &#xb7;
[Release Notes](https://github.com/pallet/pallet-repl/blob/develop/ReleaseNotes.md)

# pallet-repl

A library designed to make [pallet](http://palletops.com) easier to use at the
REPL.

## Usage

```clj
[com.palletops/pallet-repl "0.8.0-beta.1"]
```

## Features

- `use-pallet` will import the most common pallet namespaces into your
  namespace.
- `show-nodes` will print a table with info about the nodes in a
  compute service. You can define what columns are shown
  
  ```clojure
  user=> (show-nodes my-compute)
  ```
  
  will print:
  
  ```
  | :group-name | :primary-ip |           :hostname |  :private-ip | :os-family | :os-version |
  |-------------+-------------+---------------------+--------------+------------+-------------|
  |  my-group-2 |  23.22.4.48 | my-group-2-7f968312 | 10.164.34.20 |    :ubuntu |       12.04 |
  nil
  ```

- `show-group` is like `show-nodes` but it only shows nodes belonging
  go a group. 
- `explain-plan` will print the action plan corresponding to the
  execution of a plan function, and also it can print the shell
  scripts corresponding to this plan. You can provide the os-family
  and os-version to target, which default to ubuntu 12.04. 

```clojure
  (explain-plan 
      (api/plan-fn 
         (actions/remote-file "/tmp/hello_world"
                              :content "Hello World!!!")         
         (actions/exec-script "ls -la /tmp")))
  ```

  results in:
  
  ```
  NODE: ["mock-node" "mock-group" "0.0.0.0" :ubuntu :os-version "12.04"]
  ACTION: pallet.actions-impl/remote-file-action of type script executed on target
    FORM:
      (pallet.actions-impl/remote-file-action
        "/tmp/hello_world"
        {:content "Hello World!!!",
         :install-new-files true,
         :overwrite-changes nil,
         :owner nil})
    SCRIPT:
      | echo 'remote-file /tmp/hello_world...';
      | {
      | dirpath=$(dirname /var/lib/pallet/tmp/hello_world.new)
      | templatepath=$(dirname $(readlink -f /tmp/hello_world))
      | mkdir -p ${dirpath} || exit 1
      | while [ "/" != "${templatepath}" ] ;do d=${dirpath} && t=${templatepath} && dirpath=$(dirname ${dirpath}) && templatepath=$(dirname ${templatepath}) && chgrp $(stat -c%g ${t}) ${d} || : && chmod $(stat -c%a ${t}) ${d} || : && chown $(stat -c%u ${t}) ${d} || : ; done && { cat > /var/lib/pallet/tmp/hello_world.new <<EOFpallet
      | Hello World!!!
      | EOFpallet
      |  } && filediff= && if [ -e /tmp/hello_world ] && [ -e /var/lib/pallet/tmp/hello_world ]; then
      | diff -u /tmp/hello_world /var/lib/pallet/tmp/hello_world
      | filediff=$?
      | fi && md5diff= && if [ -e /var/lib/pallet/tmp/hello_world ] && [ -e /var/lib/pallet/tmp/hello_world.md5 ]; then
      | ( cd $(dirname /var/lib/pallet/tmp/hello_world.md5) && md5sum --quiet --check $(basename /var/lib/pallet/tmp/hello_world.md5) )
      | md5diff=$?
      | fi && contentdiff= && if [ -e /tmp/hello_world ] && [ -e /var/lib/pallet/tmp/hello_world.new ]; then
      | diff -u /tmp/hello_world /var/lib/pallet/tmp/hello_world.new
      | contentdiff=$?
      | fi && errexit=0 && if [ "${filediff}" == "1" ]; then
      | echo Existing file did not match the pallet master copy: FAIL
      | errexit=1
      | fi && if [ "${md5diff}" == "1" ]; then
      | echo Existing content did not match md5: FAIL
      | errexit=1
      | fi && [ "${errexit}" == "0" ] && if ! ( [ "${contentdiff}" == "0" ] ) && [ -e /var/lib/pallet/tmp/hello_world.new ]; then
      | cp -f --backup="numbered" /var/lib/pallet/tmp/hello_world.new /var/lib/pallet/tmp/hello_world
      | mv -f /var/lib/pallet/tmp/hello_world.new /tmp/hello_world
      | fi && (cp=$(readlink -f /tmp/hello_world) && cd $(dirname ${cp}) && md5sum $(basename ${cp})
      | )>/var/lib/pallet/tmp/hello_world.md5 && echo MD5 sum is $(cat /var/lib/pallet/tmp/hello_world.md5) && ls -t /tmp/hello_world.~[0-9]*~ 2> /dev/null | tail -n "+6" | xargs \
      |  rm --force
      |  } || { echo '#> remote-file /tmp/hello_world : FAIL'; exit 1;} >&2 
      | echo '#> remote-file /tmp/hello_world : SUCCESS'
  ACTION: pallet.actions/exec-script* of type script executed on target
    FORM:
      (pallet.actions/exec-script* "ls -la /tmp")
    SCRIPT:
      | ls -la /tmp
  nil
  ```
  
- `explain-phase` is similar to explain-plan, but works on a phase of
  a server-spec or group.
- `explain-session` works similar to explain-plan, but instead of
  showing what Pallet _would do_, it shows what Pallet _did_ using the
  session. Explain session can explain any session with arbitrary
  numbers of phases, groups and nodes: 
  
  ```
  user=> (def s (api/converge {my-group 1} :compute my-compute))
  ```

  results in (summarized):

  ```
  nodes created: 1
  PHASES: bootstrap, configure
  GROUPS: my-group-2
  ACTIONS:
    PHASE bootstrap:
      GROUP my-group-2:
        NODE 23.22.4.48:
          ACTION ON NODE:
            SCRIPT:
            | #!/usr/bin/env bash
                ...
            | exit $?
            EXIT CODE: 0
            OUTPUT:
            | package-manager update ...
            | #> package-manager update  : SUCCESS
          ACTION ON NODE:
            CONTEXT: [automated-admin-user: install]: 
            SCRIPT:
            | #!/usr/bin/env bash
            | echo '[automated-admin-user: install]: Packages...';
            | {
            | { debconf-set-selections <<EOF
            | debconf debconf/frontend select noninteractive
            | debconf debconf/frontend seen false
            | EOF
            | } && apt-get -q -y install sudo+ && dpkg --get-selections
            |  } || { echo '#> [automated-admin-user: install]: Packages : FAIL'; exit 1;} >&2 
            | echo '#> [automated-admin-user: install]: Packages : SUCCESS'
            | 
            | exit $?
            EXIT CODE: 0
            OUTPUT:
            | [automated-admin-user: install]: Packages...
            | Reading package lists...
            | Building dependency tree...
            | Reading state information...
            | The following packages will be upgraded:
            |   sudo
            | 1 upgraded, 0 newly installed, 0 to remove and 366 not upgraded.
            | Need to get 288 kB of archives.
            | After this operation, 0 B of additional disk space will be used.
            | Get:1 http://us-east-1.ec2.archive.ubuntu.com/ubuntu/ precise-updates/main sudo amd64 1.8.3p1-1ubuntu3.4 [288 kB]
            | Fetched 288 kB in 0s (2,135 kB/s)
            | (Reading database ... 
            | (Reading database ... 5%
            | (Reading database ... 10%
                ...
            | (Reading database ... 100%
            | (Reading database ... 161216 files and directories currently installed.)
            | Preparing to replace sudo 1.8.3p1-1ubuntu3.3 (using .../sudo_1.8.3p1-1ubuntu3.4_amd64.deb) ...
            | Unpacking replacement sudo ...
            | Processing triggers for initramfs-tools ...
            | update-initramfs: Generating /boot/initrd.img-3.2.0-33-virtual
            | Processing triggers for ureadahead ...
            | Processing triggers for man-db ...
            | Setting up sudo (1.8.3p1-1ubuntu3.4) ...
            | accountsservice					install
            | acl						install
            | acpi-support					install
            | acpid						install
            | activity-log-manager-common			install
                ...
            | zeitgeist-core					install
            | zeitgeist-datahub				install
            | zenity						install
            | zenity-common					install
            | zip						install
            | zlib1g						install
            | #> [automated-admin-user: install]: Packages : SUCCESS
          ACTION ON NODE:
            CONTEXT: [automated-admin-user]: 
            SCRIPT:
            | #!/usr/bin/env bash
            | if getent passwd tbatchelli; then /usr/sbin/usermod --shell "/bin/bash" tbatchelli;else /usr/sbin/useradd --shell "/bin/bash" --create-home tbatchelli;fi
            | exit $?
            EXIT CODE: 0
            OUTPUT:
          ACTION ON NODE:
            CONTEXT: automated-admin-user: authorize-user-key: authorize-key: 
            SCRIPT:
            | #!/usr/bin/env bash
            | echo 'automated-admin-user: authorize-user-key: authorize-key: Directory $(getent passwd tbatchelli | cut -d: -f6)/.ssh/...';
            | {
            | mkdir -m "755" -p $(getent passwd tbatchelli | cut -d: -f6)/.ssh/ && chown --recursive tbatchelli $(getent passwd tbatchelli | cut -d: -f6)/.ssh/ && chmod 755 $(getent passwd tbatchelli | cut -d: -f6)/.ssh/
            |  } || { echo '#> automated-admin-user: authorize-user-key: authorize-key: Directory $(getent passwd tbatchelli | cut -d: -f6)/.ssh/ : FAIL'; exit 1;} >&2 
            | echo '#> automated-admin-user: authorize-user-key: authorize-key: Directory $(getent passwd tbatchelli | cut -d: -f6)/.ssh/ : SUCCESS'
            | 
            | exit $?
            EXIT CODE: 0
            OUTPUT:
            | automated-admin-user: authorize-user-key: authorize-key: Directory $(getent passwd tbatchelli | cut -d: -f6)/.ssh/...
            | #> automated-admin-user: authorize-user-key: authorize-key: Directory $(getent passwd tbatchelli | cut -d: -f6)/.ssh/ : SUCCESS
          ACTION ON NODE:
            CONTEXT: automated-admin-user: authorize-user-key: authorize-key: 
            SCRIPT:
            | #!/usr/bin/env bash
            | echo 'automated-admin-user: authorize-user-key: authorize-key: file $(getent passwd tbatchelli | cut -d: -f6)/.ssh/authorized_keys...';
            | {
            | touch $(getent passwd tbatchelli | cut -d: -f6)/.ssh/authorized_keys && chown tbatchelli $(getent passwd tbatchelli | cut -d: -f6)/.ssh/authorized_keys && chmod 644 $(getent passwd tbatchelli | cut -d: -f6)/.ssh/authorized_keys
            |  } || { echo '#> automated-admin-user: authorize-user-key: authorize-key: file $(getent passwd tbatchelli | cut -d: -f6)/.ssh/authorized_keys : FAIL'; exit 1;} >&2 
            | echo '#> automated-admin-user: authorize-user-key: authorize-key: file $(getent passwd tbatchelli | cut -d: -f6)/.ssh/authorized_keys : SUCCESS'
            | 
            | exit $?
            EXIT CODE: 0
            OUTPUT:
            | automated-admin-user: authorize-user-key: authorize-key: file $(getent passwd tbatchelli | cut -d: -f6)/.ssh/authorized_keys...
            | #> automated-admin-user: authorize-user-key: authorize-key: file $(getent passwd tbatchelli | cut -d: -f6)/.ssh/authorized_keys : SUCCESS
          ACTION ON NODE:
            CONTEXT: automated-admin-user: authorize-user-key: authorize-key: 
            SCRIPT:
            | #!/usr/bin/env bash
            | echo 'automated-admin-user: authorize-user-key: authorize-key: authorize-key on user tbatchelli (ssh_key.clj:32)...';
            | {
            | auth_file=$(getent passwd tbatchelli | cut -d: -f6)/.ssh/authorized_keys && if ! ( fgrep "ssh-rsa AAAAB3N...OZQ== tbatchelli@tbatchellis-laptop-2.local" ${auth_file} ); then
            | echo "ssh-rsa AAAAB3N...OZQ== tbatchelli@tbatchellis-laptop-2.local
            | " >> ${auth_file}
            | fi
            |  } || { echo '#> automated-admin-user: authorize-user-key: authorize-key: authorize-key on user tbatchelli (ssh_key.clj:32) : FAIL'; exit 1;} >&2 
            | echo '#> automated-admin-user: authorize-user-key: authorize-key: authorize-key on user tbatchelli (ssh_key.clj:32) : SUCCESS'
            | 
            | exit $?
            EXIT CODE: 0
            OUTPUT:
            | automated-admin-user: authorize-user-key: authorize-key: authorize-key on user tbatchelli (ssh_key.clj:32)...
            | #> automated-admin-user: authorize-user-key: authorize-key: authorize-key on user tbatchelli (ssh_key.clj:32) : SUCCESS
          ACTION ON NODE:
            CONTEXT: automated-admin-user: authorize-user-key: authorize-key: 
            SCRIPT:
            | #!/usr/bin/env bash
            | echo 'automated-admin-user: authorize-user-key: authorize-key: Set selinux permissions (ssh_key.clj:37)...';
            | {
            | if hash chcon 2>&- && [ -d /etc/selinux ] && stat --format %C $(getent passwd tbatchelli | cut -d: -f6)/.ssh/ 2>&-; then chcon -Rv --type=user_home_t $(getent passwd tbatchelli | cut -d: -f6)/.ssh/;fi
            |  } || { echo '#> automated-admin-user: authorize-user-key: authorize-key: Set selinux permissions (ssh_key.clj:37) : FAIL'; exit 1;} >&2 
            | echo '#> automated-admin-user: authorize-user-key: authorize-key: Set selinux permissions (ssh_key.clj:37) : SUCCESS'
            | 
            | exit $?
            EXIT CODE: 0
            OUTPUT:
            | automated-admin-user: authorize-user-key: authorize-key: Set selinux permissions (ssh_key.clj:37)...
            | #> automated-admin-user: authorize-user-key: authorize-key: Set selinux permissions (ssh_key.clj:37) : SUCCESS
          ACTION ON NODE:
            CONTEXT: automated-admin-user: sudoers: 
            SCRIPT:
            | #!/usr/bin/env bash
            | echo 'automated-admin-user: sudoers: remote-file /etc/sudoers...';
            | {
            | dirpath=$(dirname /var/lib/pallet/etc/sudoers.new)
            | templatepath=$(dirname $(readlink -f /etc/sudoers))
            | mkdir -p ${dirpath} || exit 1
            | while [ "/" != "${templatepath}" ] ;do d=${dirpath} && t=${templatepath} && dirpath=$(dirname ${dirpath}) && templatepath=$(dirname ${templatepath}) && chgrp $(stat -c%g ${t}) ${d} || : && chmod $(stat -c%a ${t}) ${d} || : && chown $(stat -c%u ${t}) ${d} || : ; done && { cat > /var/lib/pallet/etc/sudoers.new <<EOFpallet
            | Defaults env_keep=SSH_AUTH_SOCK
            | root ALL = (ALL) ALL
            | %adm ALL = (ALL) ALL
            | tbatchelli ALL = (ALL) NOPASSWD: ALL
            | EOFpallet
            |  } && filediff= && if [ -e /etc/sudoers ] && [ -e /var/lib/pallet/etc/sudoers ]; then
            | diff -u /etc/sudoers /var/lib/pallet/etc/sudoers
            | filediff=$?
            | fi && md5diff= && if [ -e /var/lib/pallet/etc/sudoers ] && [ -e /var/lib/pallet/etc/sudoers.md5 ]; then
            | ( cd $(dirname /var/lib/pallet/etc/sudoers.md5) && md5sum --quiet --check $(basename /var/lib/pallet/etc/sudoers.md5) )
            | md5diff=$?
            | fi && contentdiff= && if [ -e /etc/sudoers ] && [ -e /var/lib/pallet/etc/sudoers.new ]; then
            | diff -u /etc/sudoers /var/lib/pallet/etc/sudoers.new
            | contentdiff=$?
            | fi && errexit=0 && if [ "${filediff}" == "1" ]; then
            | echo Existing file did not match the pallet master copy: FAIL
            | errexit=1
            | fi && if [ "${md5diff}" == "1" ]; then
            | echo Existing content did not match md5: FAIL
            | errexit=1
            | fi && [ "${errexit}" == "0" ] && if ! ( [ "${contentdiff}" == "0" ] ) && [ -e /var/lib/pallet/etc/sudoers.new ]; then
            | cp -f --backup="numbered" /var/lib/pallet/etc/sudoers.new /var/lib/pallet/etc/sudoers
            | mv -f /var/lib/pallet/etc/sudoers.new /etc/sudoers
            | fi && chown root /etc/sudoers && chgrp $(id -ng root) /etc/sudoers && chmod 0440 /etc/sudoers && (cp=$(readlink -f /etc/sudoers) && cd $(dirname ${cp}) && md5sum $(basename ${cp})
            | )>/var/lib/pallet/etc/sudoers.md5 && echo MD5 sum is $(cat /var/lib/pallet/etc/sudoers.md5) && ls -t /etc/sudoers.~[0-9]*~ 2> /dev/null | tail -n "+6" | xargs \
            |  rm --force
            |  } || { echo '#> automated-admin-user: sudoers: remote-file /etc/sudoers : FAIL'; exit 1;} >&2 
            | echo '#> automated-admin-user: sudoers: remote-file /etc/sudoers : SUCCESS'
            | 
            | exit $?
            EXIT CODE: 0
            OUTPUT:
            | automated-admin-user: sudoers: remote-file /etc/sudoers...
            | --- /etc/sudoers	2012-01-31 15:56:42.000000000 +0000
            | +++ /var/lib/pallet/etc/sudoers.new	2013-07-10 22:17:00.012723454 +0000
            | @@ -1,29 +1,4 @@
            | -#
            | -# This file MUST be edited with the 'visudo' command as root.
                 ...
            | +%adm ALL = (ALL) ALL
            | +tbatchelli ALL = (ALL) NOPASSWD: ALL
            | MD5 sum is 7c74ebd65015e958c87276681e97de4b sudoers
            | #> automated-admin-user: sudoers: remote-file /etc/sudoers : SUCCESS
    PHASE configure:
      GROUP my-group-2:
        NODE 23.22.4.48:
          ACTION ON NODE:
            SCRIPT:
            | #!/usr/bin/env bash
            | echo 'remote-file /tmp/hello_world...';
            | {
            | dirpath=$(dirname /var/lib/pallet/tmp/hello_world.new)
            | templatepath=$(dirname $(readlink -f /tmp/hello_world))
            | mkdir -p ${dirpath} || exit 1
            | while [ "/" != "${templatepath}" ] ;do d=${dirpath} && t=${templatepath} && dirpath=$(dirname ${dirpath}) && templatepath=$(dirname ${templatepath}) && chgrp $(stat -c%g ${t}) ${d} || : && chmod $(stat -c%a ${t}) ${d} || : && chown $(stat -c%u ${t}) ${d} || : ; done && { cat > /var/lib/pallet/tmp/hello_world.new <<EOFpallet
            | hello world!
            | EOFpallet
            |  } && filediff= && if [ -e /tmp/hello_world ] && [ -e /var/lib/pallet/tmp/hello_world ]; then
            | diff -u /tmp/hello_world /var/lib/pallet/tmp/hello_world
            | filediff=$?
            | fi && md5diff= && if [ -e /var/lib/pallet/tmp/hello_world ] && [ -e /var/lib/pallet/tmp/hello_world.md5 ]; then
            | ( cd $(dirname /var/lib/pallet/tmp/hello_world.md5) && md5sum --quiet --check $(basename /var/lib/pallet/tmp/hello_world.md5) )
            | md5diff=$?
            | fi && contentdiff= && if [ -e /tmp/hello_world ] && [ -e /var/lib/pallet/tmp/hello_world.new ]; then
            | diff -u /tmp/hello_world /var/lib/pallet/tmp/hello_world.new
            | contentdiff=$?
            | fi && errexit=0 && if [ "${filediff}" == "1" ]; then
            | echo Existing file did not match the pallet master copy: FAIL
            | errexit=1
            | fi && if [ "${md5diff}" == "1" ]; then
            | echo Existing content did not match md5: FAIL
            | errexit=1
            | fi && [ "${errexit}" == "0" ] && if ! ( [ "${contentdiff}" == "0" ] ) && [ -e /var/lib/pallet/tmp/hello_world.new ]; then
            | cp -f --backup="numbered" /var/lib/pallet/tmp/hello_world.new /var/lib/pallet/tmp/hello_world
            | mv -f /var/lib/pallet/tmp/hello_world.new /tmp/hello_world
            | fi && (cp=$(readlink -f /tmp/hello_world) && cd $(dirname ${cp}) && md5sum $(basename ${cp})
            | )>/var/lib/pallet/tmp/hello_world.md5 && echo MD5 sum is $(cat /var/lib/pallet/tmp/hello_world.md5) && ls -t /tmp/hello_world.~[0-9]*~ 2> /dev/null | tail -n "+6" | xargs \
            |  rm --force
            |  } || { echo '#> remote-file /tmp/hello_world : FAIL'; exit 1;} >&2 
            | echo '#> remote-file /tmp/hello_world : SUCCESS'
            | 
            | exit $?
            EXIT CODE: 0
            OUTPUT:
            | remote-file /tmp/hello_world...
            | MD5 sum is c897d1410af8f2c74fba11b1db511e9e hello_world
            | #> remote-file /tmp/hello_world : SUCCESS
          ACTION ON NODE:
            SCRIPT:
            | #!/usr/bin/env bash
            | touch /tmp/touched
            | exit $?
            EXIT CODE: 0
            OUTPUT:
  nil
  ```

- `session-summary` provides the final result of executing each phase on each node.

  ```clojure
   (session-summary s)
   ```
   
   results in:
   
   ```
   nodes created: 1
   PHASES: bootstrap, configure
   GROUPS: my-group-2
   ACTIONS:
     PHASE bootstrap:
       GROUP my-group-2:
         NODE 23.22.4.48: OK
     PHASE configure:
       GROUP my-group-2:
         NODE 23.22.4.48: OK
   nil
   ```
   
Please check the docstring for each command for information about the
available options.

## License

Copyright © 2013 Hugo Duncan, Antoni Batchelli

Distributed under the Eclipse Public License.
