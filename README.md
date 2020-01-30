## Jenkins Dependency Update Scanner Plugin (maven)
My first Jenkins plugin, which invokes a maven command to scan project dependencies for updates, collects scan info 
and sends it to your Slack workspace and channel which you specify. Current implementation fires a POST request
to `{<yourWorkspace>.slack.com}/api/chat.postMessage` with a payload containing the scan info. Next update will have 
the `webhook` template embedded.

### Prerequisites
1. Maven (3.6.2)
2. Jenkins (ADMINISTRATION role)

### Installation
1. Clone the repo
2. Navigate to project root
3. Run `mvn hpi:hpi`
4. Go to `Jenkins Management/Plugins/Advanced`
5. Select `Upload Plugin`
6. Select the generated hpi file in `{projectRoot}/target/maven-dependency-scanner.hpi`
7. Done!

### Usage
Navigate to your project build/pipeline. On the left side of the screen you will see the `Manage` option. Click it, and scroll 
down until you see the `Build` subsection. In there click `Add` and if everything above is done correctly, you should see the `DependencyScannerBuilder`
module. Click on that and you will be prompted to enter information about your `workspace`, `channel` and `credentials` on Slack.
Fill it in. In the credentials seciton press add, and select `Slack token` as credentials type. Paste in the token you were
provided from your Slack api (looks something like this: `xoxb-XXXXXXXXXXXX-XXXXXXXXXXXX-XXXXXXXXXXXXXXXXXXXXXXXX`). That's it!

Schedule a build and you should recieve a message containing the information about outdated dependencies and latest versions.
