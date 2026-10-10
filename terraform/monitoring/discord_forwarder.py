"""Posts what reaches the alerts SNS topic to Discord (monitoring.tf).

Three kinds of message arrive:
  - AMP alertmanager: already Discord-formatted text (alertmanager.yaml), channel in a message attribute
  - CloudWatch alarm: JSON — AlarmDescription is "title: details"
  - Budgets, GuardDuty (EventBridge): plain text
Webhook URLs are read from SSM once per container — they're set by hand, never in code or env.
"""
import json
import os
import urllib.request

DISCORD_LIMIT = 2000
_webhooks = {}


def webhook(channel):
    name = os.environ["MESSAGING_WEBHOOK_PARAM" if channel == "messaging" else "SERVICE_WEBHOOK_PARAM"]
    if name not in _webhooks:
        import boto3  # in the Lambda runtime; imported here so the self-check below runs without it

        _webhooks[name] = boto3.client("ssm").get_parameter(Name=name, WithDecryption=True)["Parameter"]["Value"]
    return _webhooks[name]


def text(message):
    try:
        alarm = json.loads(message)
    except ValueError:
        return message
    if not isinstance(alarm, dict) or "AlarmName" not in alarm:
        return message

    title, _, details = (alarm.get("AlarmDescription") or alarm["AlarmName"]).partition(": ")
    if alarm["NewStateValue"] == "OK":
        return f"✅  **{title} (해제)**"
    return f"🚨  **{title}**\n\n**사유**: {alarm['NewStateReason']}\n\n**조치**: {details}"


def handler(event, _context):
    for record in event["Records"]:
        sns = record["Sns"]
        channel = sns.get("MessageAttributes", {}).get("channel", {}).get("Value", "service")
        body = json.dumps({"content": text(sns["Message"])[:DISCORD_LIMIT]}).encode()
        # Discord's edge refuses the default Python-urllib agent.
        request = urllib.request.Request(
            webhook(channel), body, {"Content-Type": "application/json", "User-Agent": "modudrive-alerts"}
        )
        urllib.request.urlopen(request, timeout=10)


if __name__ == "__main__":
    assert text("plain budget text") == "plain budget text"
    alarm = {
        "AlarmName": "alb-5xx",
        "AlarmDescription": "ALB 5xx: gateway 태스크 상태 확인",
        "NewStateValue": "ALARM",
        "NewStateReason": "Threshold Crossed",
    }
    assert text(json.dumps(alarm)) == "🚨  **ALB 5xx**\n\n**사유**: Threshold Crossed\n\n**조치**: gateway 태스크 상태 확인"
    assert text(json.dumps({**alarm, "NewStateValue": "OK"})) == "✅  **ALB 5xx (해제)**"
    assert text('"a json string"') == '"a json string"'
    print("ok")
