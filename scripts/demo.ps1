param([string]$BaseUrl = 'http://localhost:8080', [string]$AdminPassword = 'demo-admin-password')
$ErrorActionPreference = 'Stop'
function Basic($Name, $Password) {
    @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${Name}:${Password}")) }
}
function Send($Method, $Path, $Headers, $Body) {
    $params = @{ Method=$Method; Uri="$BaseUrl$Path"; Headers=$Headers }
    if ($null -ne $Body) { $params.ContentType='application/json'; $params.Body=($Body | ConvertTo-Json) }
    Invoke-RestMethod @params
}
$username = 'demo-' + [Guid]::NewGuid().ToString('N').Substring(0,12)
$password = 'demo-user-password'
$admin = Basic 'admin' $AdminPassword
$user = Basic $username $password
$account = Send POST '/api/v1/users' @{} @{username=$username; password=$password}
$item = Send POST '/api/v1/equipment' $admin @{name='Demo camera'; description='Camera with lens'; quantity=2}
$reservation = Send POST '/api/v1/reservations' $user @{equipmentId=$item.id; quantity=1}
Send POST "/api/v1/admin/reservations/$($reservation.id)/issue" $admin $null
Send POST "/api/v1/admin/reservations/$($reservation.id)/return" $admin $null
Send GET "/api/v1/reservations/$($reservation.id)/events" $user $null
$final = Send GET "/api/v1/equipment/$($item.id)" $user $null
if ($final.availableQuantity -ne 2) { throw 'Stock was not restored' }
Write-Output 'PASS: registration, reservation, issue, return, audit, stock restoration'
