# Distributed AI Flow Report

- Generated: 2026-06-11 11:01:50 +08:00
- Gateway: `http://localhost:18080`
- AI service: `http://localhost:8106`
- Summary: PASS=5, FAIL=0

| Status | Check | Detail |
| --- | --- | --- |
| PASS | gateway login | student and company tokens issued |
| PASS | DashScope configured | provider=dashscope, model=qwen-plus |
| PASS | real DashScope candidate screening | mocked=false, score=78 |
| PASS | delivery created | deliveryId=D653e18bc |
| PASS | RocketMQ delivery-to-screening flow | taskId=AST-c321c1ab, status=COMPLETED, count=1 |
