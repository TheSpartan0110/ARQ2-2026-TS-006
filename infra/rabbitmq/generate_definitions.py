#!/usr/bin/env python3
"""Generates definitions.json (RabbitMQ topology from docs/spec.md section 4).

Run:  python3 infra/rabbitmq/generate_definitions.py
Local development passwords only: <service>_mq_dev_pw (override with <SERVICE>_MQ_PASSWORD env vars).
"""
import base64, hashlib, json, os, pathlib

EVENTS, COMMANDS = "eatsnow.events.v1", "eatsnow.commands.v1"
DLX, RETRY = "eatsnow.dlx.v1", "eatsnow.retry.v1"
VHOST = "/"
RETRY_DELAYS = {"5s": 5_000, "30s": 30_000, "120s": 120_000}   # spec 4.3 item 4

# queue -> (exchange, routing keys)   (spec 4.2.1)
QUEUES = {
    "orders.events": (EVENTS, ["OrderCreated", "PaymentAuthorized", "PaymentFailed",
                               "PaymentPendingReconciliation", "PaymentRefunded",
                               "RestaurantAccepted", "RestaurantRejected", "RestaurantPreparing",
                               "DeliveryAssigned", "DeliveryPickedUp", "DeliveryCompleted"]),
    "payments.commands": (COMMANDS, ["ChargePayment", "RefundPayment"]),
    "restaurants.commands": (COMMANDS, ["RequestRestaurantAcceptance"]),
    "delivery.commands": (COMMANDS, ["AssignDelivery"]),
    "notifications.events": (EVENTS, ["OrderStatusChanged"]),
}

# service -> (queue it consumes, exchanges it publishes business messages to)
SERVICES = {
    "orders": ("orders.events", [EVENTS, COMMANDS]),
    "payments": ("payments.commands", [EVENTS]),
    "restaurants": ("restaurants.commands", [EVENTS]),
    "delivery": ("delivery.commands", [EVENTS]),
    "notifications": ("notifications.events", []),
}


def password_hash(user: str, password: str) -> str:
    """RabbitMQ rabbit_password_hashing_sha256: base64(salt(4) + sha256(salt + password))."""
    salt = hashlib.sha256(user.encode()).digest()[:4]          # deterministic -> stable diffs
    return base64.b64encode(salt + hashlib.sha256(salt + password.encode()).digest()).decode()


def user_entry(name, password, tags=""):
    return {"name": name, "password_hash": password_hash(name, password),
            "hashing_algorithm": "rabbit_password_hashing_sha256", "tags": tags}


def exchange(name, kind):
    return {"name": name, "vhost": VHOST, "type": kind, "durable": True,
            "auto_delete": False, "internal": False, "arguments": {}}


def queue(name, args):
    return {"name": name, "vhost": VHOST, "durable": True, "auto_delete": False,
            "arguments": {"x-queue-type": "classic", **args}}


def binding(src, dst, key):
    return {"source": src, "vhost": VHOST, "destination": dst, "destination_type": "queue",
            "routing_key": key, "arguments": {}}


users = [user_entry("eatsnow_admin", os.getenv("RABBITMQ_ADMIN_PASSWORD", "admin_dev_pw"), "administrator")]
permissions = [{"user": "eatsnow_admin", "vhost": VHOST, "configure": ".*", "write": ".*", "read": ".*"}]
exchanges = [exchange(EVENTS, "topic"), exchange(COMMANDS, "topic"),
             exchange(DLX, "direct"), exchange(RETRY, "direct")]
queues, bindings = [], []

for q, (ex, keys) in QUEUES.items():
    # Main queue: a rejected (nack, requeue=false) message goes to the DLX with the queue name as key.
    queues.append(queue(q, {"x-dead-letter-exchange": DLX, "x-dead-letter-routing-key": q}))
    queues.append(queue(f"{q}.dlq", {}))
    bindings.append(binding(DLX, f"{q}.dlq", q))
    bindings += [binding(ex, q, k) for k in keys]
    # Retry queues: a message waits for its TTL, then returns to the main queue via the default exchange.
    for label, ttl in RETRY_DELAYS.items():
        rq = f"{q}.retry.{label}"
        queues.append(queue(rq, {"x-message-ttl": ttl, "x-dead-letter-exchange": "",
                                 "x-dead-letter-routing-key": q}))
        bindings.append(binding(RETRY, rq, rq))

for svc, (consumes, publishes) in SERVICES.items():
    name = f"{svc}_mq"
    users.append(user_entry(name, os.getenv(f"{svc.upper()}_MQ_PASSWORD", f"{svc}_mq_dev_pw")))
    # write: own business exchanges + retry/dlx exchanges (consumer failure handling)
    writable = [e.replace(".", r"\.") for e in publishes + [RETRY, DLX]]
    permissions.append({"user": name, "vhost": VHOST, "configure": "^$",
                        "write": "^(" + "|".join(writable) + ")$",
                        "read": "^" + consumes.replace(".", r"\.") + "$"})

definitions = {"vhosts": [{"name": VHOST}], "users": users, "permissions": permissions,
               "exchanges": exchanges, "queues": queues, "bindings": bindings}
out = pathlib.Path(__file__).with_name("definitions.json")
out.write_text(json.dumps(definitions, indent=2) + "\n")
print(f"wrote {out} ({len(queues)} queues, {len(bindings)} bindings, {len(users)} users)")
