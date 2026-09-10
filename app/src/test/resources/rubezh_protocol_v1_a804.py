"""Copied from Rubejh SYNC_COMMIT a804bf6c9715a93c1b3fd2bd0fd9c9edf2b75bc4."""

from __future__ import annotations

import hashlib
import json
from typing import Any

PROTOCOL_VERSION = 1
PROTOCOL_NAME = "rubezh-sync"

DEFAULT_SYNC_PORT = 8765
MAX_REQUEST_BYTES = 1_048_576
PAIRING_TOKEN_TTL_SEC = 120
MAX_FAILED_AUTH_PER_MINUTE = 30

PATH_HEALTH = "/v1/health"
PATH_PAIR_REQUEST = "/v1/pair/request"
PATH_PAIR_STATUS = "/v1/pair/status"
PATH_PACKAGES_PULL = "/v1/packages/pull"
PATH_PACKAGES_PUSH = "/v1/packages/push"
PATH_PACKAGES_ACK = "/v1/packages/ack"

ERR_UNAUTHORIZED = "unauthorized"
ERR_FORBIDDEN = "forbidden"
ERR_REVOKED = "device_revoked"
ERR_BAD_TOKEN = "bad_pairing_token"
ERR_TOKEN_EXPIRED = "pairing_token_expired"
ERR_TOKEN_USED = "pairing_token_used"
ERR_PAIRING_PENDING = "pairing_pending"
ERR_PAIRING_DENIED = "pairing_denied"
ERR_PROTOCOL = "unsupported_protocol"
ERR_PAYLOAD = "invalid_payload"
ERR_TOO_LARGE = "request_too_large"
ERR_RATE_LIMIT = "rate_limited"
ERR_NOT_FOUND = "not_found"
ERR_CONFLICT_STATE = "conflict_state"
ERR_INTERNAL = "internal_error"
ERR_DEMO = "demo_not_allowed"
ERR_DISABLED = "sync_disabled"
ERR_SENDER_MISMATCH = "sender_mismatch"
ERR_PACKAGE_REJECTED = "package_rejected"
ERR_UNRESOLVED_DEPENDENCY = "unresolved_dependency"


def canonical_json(data: Any) -> str:
    return json.dumps(data, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def version_hash(payload: dict[str, Any]) -> str:
    return hashlib.sha256(canonical_json(payload).encode("utf-8")).hexdigest()
