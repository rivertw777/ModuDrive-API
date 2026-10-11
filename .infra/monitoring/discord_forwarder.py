"""Posts what reaches the alerts SNS topic to Discord (monitoring.tf).

Three kinds of message arrive (each posted as the text plus a coloured title card):
  - AMP alertmanager: already Discord-formatted text (alertmanager.yaml), channel in a message attribute
  - CloudWatch alarm: JSON — AlarmDescription is "title: details"
  - Budgets, GuardDuty (EventBridge): plain text
Webhook URLs are read from SSM once per container — they're set by hand, never in code or env.
"""
import json
import os
import urllib.request

DISCORD_LIMIT = 2000
FIRING, RESOLVED, OTHER = 0xE5484D, 0x2EA043, 0x8B949E
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


def payload(message):
    """The text as the message, plus the small coloured card Grafana used to add: the first line's title,
    red while firing, green once resolved (the text starts with 🚨 / ✅), grey for anything else."""
    body = text(message)
    first = body.split("\n", 1)[0]
    color = FIRING if first.startswith("🚨") else RESOLVED if first.startswith("✅") else OTHER
    title = first.lstrip("🚨✅ ").replace("**", "").strip()
    return {
        "content": body[:DISCORD_LIMIT],
        "embeds": [{"title": title[:256], "color": color, "footer": {"text": "ModuDrive · Alertmanager"}}],
    }


def handler(event, _context):
    for record in event["Records"]:
        sns = record["Sns"]
        channel = sns.get("MessageAttributes", {}).get("channel", {}).get("Value", "service")
        body = json.dumps(payload(sns["Message"])).encode()
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
    card = payload("🚨  **서비스 응답 없음** (2건)\n\n- a")["embeds"][0]
    assert (card["title"], card["color"]) == ("서비스 응답 없음 (2건)", FIRING)
    card = payload(json.dumps({**alarm, "NewStateValue": "OK"}))["embeds"][0]
    assert (card["title"], card["color"]) == ("ALB 5xx (해제)", RESOLVED)
    assert payload("plain budget text")["embeds"][0]["color"] == OTHER
    print("ok")
