"""Posts what reaches the alerts SNS topic to Discord (monitoring.tf).

Three kinds of message arrive (each posted as the text plus a coloured title card):
  - AMP alertmanager: already Discord-formatted text (alertmanager.yaml), channel in a message attribute
  - CloudWatch alarm: JSON — AlarmDescription is "title: details", channel from the alarm name's prefix
  - Budgets: plain text; GuardDuty (EventBridge): a JSON string of text. Both the account channel
Webhook URLs are read from SSM once per container — they're set by hand, never in code or env. A channel
whose URL isn't set yet (still "set-by-hand") falls back to the service channel instead of dropping the alert.
"""
import json
import os
import urllib.request

DISCORD_LIMIT = 2000
FIRING, RESOLVED, OTHER = 0xE5484D, 0x2EA043, 0x8B949E
# A blank line between the text and the card. Discord trims trailing newlines, so a zero-width space holds it.
CARD_GAP = "\n\u200b"
_webhooks = {}


# CloudWatch alarms can't carry a message attribute, so their names say where they go (monitoring.tf).
ALARM_CHANNELS = {"queue-age-": "messaging", "service-": "infra", "db-": "infra", "redis-": "infra", "nat-": "infra"}


def channel_of(sns):
    attribute = sns.get("MessageAttributes", {}).get("channel", {}).get("Value")
    if attribute:
        return attribute
    try:
        alarm = json.loads(sns["Message"])
    except ValueError:
        return "account"
    if not isinstance(alarm, dict) or "AlarmName" not in alarm:
        return "account"
    return next((c for prefix, c in ALARM_CHANNELS.items() if alarm["AlarmName"].startswith(prefix)), "service")


def url(channel):
    name = os.environ.get(f"{channel.upper()}_WEBHOOK_PARAM", os.environ["SERVICE_WEBHOOK_PARAM"])
    if name not in _webhooks:
        import boto3  # in the Lambda runtime; imported here so the self-check below runs without it

        _webhooks[name] = boto3.client("ssm").get_parameter(Name=name, WithDecryption=True)["Parameter"]["Value"]
    return _webhooks[name]


def webhook(channel):
    value = url(channel)
    return value if value.startswith("https://") or channel == "service" else url("service")


def text(message):
    try:
        alarm = json.loads(message)
    except ValueError:
        return message
    if isinstance(alarm, str):
        return alarm  # EventBridge's string template (GuardDuty) arrives quoted and escaped
    if not isinstance(alarm, dict) or "AlarmName" not in alarm:
        return message

    # A new alarm (or one back from missing data) goes INSUFFICIENT_DATA → OK: nothing was ever firing.
    if alarm["NewStateValue"] == "OK" and alarm.get("OldStateValue") != "ALARM":
        return None
    title, _, details = (alarm.get("AlarmDescription") or alarm["AlarmName"]).partition(": ")
    target = f"**대상**: `{alarm['AlarmName']}`"
    if alarm["NewStateValue"] == "OK":
        return f"✅  **{title} (해제)**\n\n{target}"
    return f"🚨  **{title}**\n\n{target}\n\n**사유**: {alarm['NewStateReason']}\n\n**조치**: {details}"


def payload(message):
    """The text as the message, plus a small card titled with its first line: red while firing, green
    once resolved (the text starts with 🚨 / ✅), grey for anything else. None when there's nothing to post."""
    body = text(message)
    if body is None:
        return None
    first = body.split("\n", 1)[0]
    color = FIRING if first.startswith("🚨") else RESOLVED if first.startswith("✅") else OTHER
    title = first.lstrip("🚨✅ ").replace("**", "").strip()
    return {
        "content": body[: DISCORD_LIMIT - len(CARD_GAP)] + CARD_GAP,
        "embeds": [{"title": title[:256], "color": color, "footer": {"text": "ModuDrive"}}],
    }


def handler(event, _context):
    for record in event["Records"]:
        sns = record["Sns"]
        message = payload(sns["Message"])
        if message is None:
            continue
        channel = channel_of(sns)
        body = json.dumps(message).encode()
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
        "OldStateValue": "OK",
        "NewStateReason": "Threshold Crossed",
    }
    resolved = {**alarm, "NewStateValue": "OK", "OldStateValue": "ALARM"}
    assert text(json.dumps(alarm)) == (
        "🚨  **ALB 5xx**\n\n**대상**: `alb-5xx`\n\n**사유**: Threshold Crossed\n\n**조치**: gateway 태스크 상태 확인"
    )
    assert text(json.dumps(resolved)) == "✅  **ALB 5xx (해제)**\n\n**대상**: `alb-5xx`"
    assert payload(json.dumps({**resolved, "OldStateValue": "INSUFFICIENT_DATA"})) is None  # just created
    assert text('"🚨  **GuardDuty**\\n\\nx"') == "🚨  **GuardDuty**\n\nx"
    assert payload('"🚨  **GuardDuty**\\n\\nx"')["embeds"][0]["color"] == FIRING
    card = payload("🚨  **서비스 응답 없음** (2건)\n\n- a")["embeds"][0]
    assert (card["title"], card["color"]) == ("서비스 응답 없음 (2건)", FIRING)
    card = payload(json.dumps(resolved))["embeds"][0]
    assert (card["title"], card["color"]) == ("ALB 5xx (해제)", RESOLVED)
    assert payload("plain budget text")["embeds"][0]["color"] == OTHER
    assert payload("plain budget text")["content"] == "plain budget text\n\u200b"
    assert len(payload("x" * 3000)["content"]) == DISCORD_LIMIT

    def sns(message, attribute=None):
        return {"Message": message, **({"MessageAttributes": {"channel": {"Value": attribute}}} if attribute else {})}

    assert channel_of(sns("🚨 text", "messaging")) == "messaging"
    assert channel_of(sns(json.dumps({**alarm, "AlarmName": "queue-age-member-signed-up"}))) == "messaging"
    assert channel_of(sns(json.dumps({**alarm, "AlarmName": "service-cpu-file"}))) == "infra"
    assert channel_of(sns(json.dumps({**alarm, "AlarmName": "db-memory-file"}))) == "infra"
    assert channel_of(sns(json.dumps(alarm))) == "service"  # alb-5xx
    assert channel_of(sns("plain budget text")) == "account"

    os.environ.update(SERVICE_WEBHOOK_PARAM="s", INFRA_WEBHOOK_PARAM="i", ACCOUNT_WEBHOOK_PARAM="a")
    _webhooks.update(s="https://service", i="https://infra", a="set-by-hand")
    assert webhook("infra") == "https://infra"
    assert webhook("account") == "https://service"  # not set yet → falls back
    assert webhook("unknown") == "https://service"
    print("ok")
