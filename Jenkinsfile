#!groovy

def releaseJob = env.JOB_NAME.split('/', 2)[0].contains('RELEASE')
def buildType = releaseJob ? 'release' : 'snapshot'
echo "buildType set to $buildType based on job name $env.JOB_NAME"

properties([
        disableConcurrentBuilds(),
        pipelineTriggers(releaseJob ? [] : [[$class: "GitHubPushTrigger"]]),
        [$class: 'ParametersDefinitionProperty',
         parameterDefinitions:
                 [[$class: 'BooleanParameterDefinition',
                   name: 'CONFIRM_RELEASE',
                   defaultValue: false,
                   description: "Check this box to confirm the release."]]]
])

if (releaseJob && !params.CONFIRM_RELEASE) {
    echo "The release was not confirmed, aborting as success"
    return
}

def buildVersion
def gitPrevTag
def gitCreds = [[$class: 'UsernamePasswordMultiBinding',
                 credentialsId: 'github-user-pass',
                 usernameVariable: 'GIT_USERNAME',
                 passwordVariable: 'GIT_PASSWORD']]

def runMaven = { tasks ->
    def mvnHome = tool name: 'MAVEN3', type: 'maven'
    def javaHome = tool name: 'JDK8', type: 'jdk'
    withEnv(["JAVA_HOME=$javaHome"]) {
        sh "${mvnHome}/bin/mvn --debug --batch-mode $tasks "
    }
}
def projectRepo
def repoWithCreds = { url ->
    url.replace(
            'https://',
            'https://' + env.GIT_USERNAME + ':' + env.GIT_PASSWORD + '@')
}
def repoName = { url ->
    url.substring(url.lastIndexOf('/') + 1).replace('.git', '')
}

node {
    stage("Checkout") {
        checkout scm

        // Refresh tags, in case something was aborted in a previous run
        sh "git tag -l | xargs git tag -d"
        sh "git fetch --all"

        projectRepo = sh script: "git ls-remote --get-url",
                returnStdout: true
        projectRepo = projectRepo.trim().replace('.git', '')
        echo "Project URL is $projectRepo"
    }

    stage("Versioning") {
        def mavenPom = readMavenPom file: 'pom.xml'
        echo "POM version is ${mavenPom.version}"

        buildVersion = mavenPom.version.trim().replace('-SNAPSHOT', '')

        if (env.BRANCH_NAME != 'master') {
            buildVersion += '-' + env.BRANCH_NAME
        }

        if (buildType == 'snapshot') {
            buildVersion += '-SNAPSHOT'
        } else if (buildType == 'release') {
            echo "Determining existing tag versions of $buildVersion"
            def tags = sh script: "git tag --list $buildVersion*",
                    returnStdout: true
            echo tags
            existingTagVersions = [0]
            for (String tag in tags.split('\\s')) {
                tag = tag.trim()
                if (tag == '') continue
                existingTagVersions <<
                        (int) ((tag.tokenize('-')[-1]).toInteger())
            }
            existingVersionsCnt = existingTagVersions.size()
            maxExistingVersion = String.valueOf(existingTagVersions.max())
            thisVersion = String.valueOf(existingTagVersions.max() + 1)
            if (existingVersionsCnt > 1) {
                echo "Found ${existingVersionsCnt - 1} existing tag versions"
                echo "Highest tag version found is $maxExistingVersion"
            } else {
                echo "No existing tags versions found"
            }
            buildVersion += '-' + thisVersion
        }
        echo "buildNumber version $buildVersion"
    }

    stage("Build") {
        echo "Setting POM version numbers to $buildVersion"
        runMaven "versions:set -DnewVersion=$buildVersion"
        try {
            if (releaseJob) {
                runMaven "--update-snapshots clean deploy"
            } else {
                runMaven "clean deploy"
            }
        } finally {
            echo "Reverting POM version numbers"
            runMaven "versions:revert"
        }
    }

    stage("Publish") {
        if (buildType == 'release') {
            echo "Finding previous tag"
            try {
                gitPrevTag = sh script: "git describe --abbrev=0",
                        returnStdout: true
                gitPrevTag = gitPrevTag.trim()
            } catch (Exception e) {
                gitPrevTag == ''
            }
            echo "Tagging the release"
            def tagMessage = 'Tagging release from Jenkins'
            sh "git tag -a -m '$tagMessage' $buildVersion"
            try {
                withCredentials(gitCreds) {
                    sh "git push --tags ${repoWithCreds projectRepo}"
                }
            }
            catch (Exception e) {
                sh "git tag --delete $buildVersion"
                throw e
            }
        }
    }

    if (releaseJob) {
        stage("Release notes") {
            if (gitPrevTag == '') {
                echo "No previous tags found, cannot do release notes"
                return
            }
            def releaseNotesScript = load 'release-notes.groovy'
            def templateStr = readFile 'release-notes-template.md'
            def gitLogFormat = releaseNotesScript.getGitLogFormat()
            def gitLogStr = sh script:
                    "git log $gitPrevTag..$buildVersion " +
                            "$gitLogFormat",
                    returnStdout: true
            
            if (gitLogStr.trim() == '') {
                echo "Skipping release notes, git log returned nothing"
                return
            }
            gitLogStr = releaseNotesScript.sanitizeGitLogStr(gitLogStr)

            def releaseNotesStr = releaseNotesScript
                    .generateReleaseNotes(projectRepo,
                    buildVersion, templateStr, gitLogStr)
            echo "$releaseNotesStr"

            writeFile text: """{"tag_name": "$buildVersion", "body": 
"$releaseNotesStr"}""",
                    file: "release-notes-${buildVersion}.json"

            def projectName = repoName projectRepo
            sleep 5
            withCredentials(gitCreds) {
                sh """curl --user "$env.GIT_USERNAME:$env.GIT_PASSWORD" \
--data @release-notes-${buildVersion}.json \
https://github.hpe.com/api/v3/repos/prp/$projectName/releases"""
            }
        }
    }
}
