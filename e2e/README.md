# e2e 冒烟测试

本目录提供基础冒烟测试脚本（不含业务功能）。

## smoke.ps1

验证两个后端 health 端点可访问，且前端开发服务器的代理配置可达（可选）。

```powershell
# 先启动后端（或全部服务）
powershell -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1

# 运行冒烟测试
powershell -ExecutionPolicy Bypass -File .\e2e\smoke.ps1
```

本阶段仅验证框架级健康检查，不包含任何业务测试。
