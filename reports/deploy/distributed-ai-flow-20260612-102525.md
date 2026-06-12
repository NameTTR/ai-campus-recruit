# Distributed AI Flow Report

- Generated: 2026-06-12 10:25:25 +08:00
- Gateway: `http://192.168.6.130:8080`
- AI service: `http://192.168.6.142:8106`
- Summary: PASS=5, FAIL=0

| Status | Check | Detail |
| --- | --- | --- |
| PASS | gateway login | student and company tokens issued |
| PASS | DashScope configured | provider=dashscope, model=qwen-plus |
| PASS | real DashScope candidate screening | mocked=false, score=82 |
| PASS | delivery created | deliveryId=D214e6cf0 |
| PASS | RocketMQ delivery-to-screening flow | taskId=AST-9109cad9, status=COMPLETED, count=1 |
