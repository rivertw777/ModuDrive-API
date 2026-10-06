# Copies every mail LocalStack's SES has accepted into Mailpit, so local mail shows up in an inbox at
# http://localhost:8025. LocalStack only relays SES over SMTP on its Ultimate plan; this polls its
# /_aws/ses store instead and replays each new message to Mailpit over SMTP.
import json, smtplib, time, urllib.error, urllib.request
from email.message import EmailMessage
from email.parser import BytesParser
from email.utils import getaddresses

SES_STORE = "http://localstack:4566/_aws/ses"
MAILPIT = ("mailpit", 1025)

# ponytail: in-memory only, so a restart of this container alone replays what LocalStack still holds
# (duplicates in Mailpit). Both lose everything on restart anyway, so they rarely drift apart.
seen = set()


def envelope(m):
    """(sender, recipients, raw bytes) for a SendRawEmail or a SendEmail record."""
    if m.get("RawData"):
        raw = m["RawData"].encode()
        headers = BytesParser().parsebytes(raw, headersonly=True)
        rcpts = [a for _, a in getaddresses(headers.get_all("To", []) + headers.get_all("Cc", []))]
        return m.get("Source"), rcpts, raw
    msg = EmailMessage()
    msg["From"], msg["Subject"] = m["Source"], m.get("Subject", "")
    rcpts = m.get("Destination", {}).get("ToAddresses", [])
    msg["To"] = ", ".join(rcpts)
    body = m.get("Body", {})
    msg.set_content(body.get("text_part") or "")
    if body.get("html_part"):
        msg.add_alternative(body["html_part"], subtype="html")
    return m["Source"], rcpts, msg.as_bytes()


while True:
    try:
        with urllib.request.urlopen(SES_STORE, timeout=5) as r:
            messages = json.load(r)["messages"]
        for m in messages:
            if m["Id"] in seen:
                continue
            sender, rcpts, raw = envelope(m)
            with smtplib.SMTP(*MAILPIT, timeout=5) as smtp:
                smtp.sendmail(sender, rcpts, raw)
            seen.add(m["Id"])
            print(f"forwarded {m['Id']} to {rcpts}", flush=True)
    except urllib.error.HTTPError as e:
        if e.code != 404:  # LocalStack answers 404 until SES has stored its first mail
            print(f"retrying: {e}", flush=True)
    except Exception as e:  # LocalStack or Mailpit still starting / restarting: try again next round
        print(f"retrying: {e}", flush=True)
    time.sleep(2)
