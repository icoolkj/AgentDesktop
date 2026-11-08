# 复制为 env.local.ps1（已 gitignore），按本机路径修改
$env:MAVEN_HOME = "D:\Maven\apache-maven-3.9.9"
$env:JAVA_HOME = "D:\Program Files\Java\jdk-21.0.6"
# Electron spawn jar 时也会读 AGENT_JAVA_HOME（优先于系统 JAVA_HOME）
$env:AGENT_JAVA_HOME = "D:\Program Files\Java\jdk-21.0.6"
# 日常开发：IDEA 跑后端时设置，Electron 直连不 spawn Java（端口以 IDEA 控制台为准）
$env:AGENT_WS_URL = "ws://127.0.0.1:5566/ws"
$env:Path = "$env:MAVEN_HOME\bin;$env:JAVA_HOME\bin;" + $env:Path
