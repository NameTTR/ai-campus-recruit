# Distributed AI Flow Report

- Generated: 2026-06-12 10:38:29 +08:00
- Gateway: `http://192.168.6.130:8080`
- AI service: `http://192.168.6.142:8106`
- Summary: PASS=5, FAIL=0

| Status | Check | Detail |
| --- | --- | --- |
| PASS | gateway login | student and company tokens issued |
| PASS | DashScope configured | provider=dashscope, model=qwen-plus |
| PASS | real DashScope candidate screening | mocked=false, score=78 |
| PASS | delivery created | deliveryId=D24291fd2 |
| PASS | RocketMQ delivery-to-screening flow | taskId=AST-5696f160, status=COMPLETED, count=1 |
