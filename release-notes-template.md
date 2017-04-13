### Notes from developers

<% commits.each { commit ->
if (commit.head.endsWith('~')) {
    println '* ' + commit.head.replaceAll('~$', '') }} %>

### Raw commits

<% commits.each { commit ->
println '*' + commit.auth + '*'
println ''
println '> ' + commit.head
if (commit.body != '') {
    println ""
    println '> ' + commit.body
}
println "" } %>