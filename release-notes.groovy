import groovy.json.JsonSlurper
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import groovy.text.TemplateEngine

@NonCPS
def String getGitLogFormat() {
    return '''--pretty=format:'{\n\
\t@&@hash@&@: @&@%H@&@, \n\
\t@&@auth@&@: @&@%aN <%aE>@&@, \n\
\t@&@time@&@: @&@%at@&@, \n\
\t@&@head@&@: @&@%s@&@, \n\
\t@&@body@&@: @&@%b@&@\n},' '''
}

@NonCPS
def String sanitizeGitLogStr(String gitLogStr) {
    gitLogStr = gitLogStr.replaceAll('\\"', '\\\\"')
    gitLogStr = gitLogStr.replaceAll('@&@', '"')
    gitLogStr = '[' + gitLogStr.replaceAll(',$', ']')
    return gitLogStr
}

@NonCPS
def String generateReleaseNotes(String originUrl, String tagName,
        String templateStr, String gitLogStr) {
    JsonSlurper jsonSlurper = new JsonSlurper()
    def commits = jsonSlurper.parseText(gitLogStr)

    def binding = [
            originUrl: originUrl,
            tagName: tagName,
            commits: commits
    ]

    TemplateEngine engine = new SimpleTemplateEngine()
    Template template = engine.createTemplate(templateStr)
    String retStr = template.make(binding).toString()
    return retStr.replace('"', '\\"').replaceAll('\\n', '\\\\n')
}

return this
