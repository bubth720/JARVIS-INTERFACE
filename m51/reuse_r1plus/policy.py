"""Bounded observations for the C1 laboratory; never grants runtime authority."""
import re
import time

CAMPAIGN = "REUSE-R1PLUS-C1"


class WifiObservationError(RuntimeError):
    def __init__(self, reason, observations, elapsed):
        super().__init__(reason)
        self.evidence = {"status": "BLOCKED", "reason": reason,
                         "samples": observations, "elapsed_seconds": elapsed}


def wait_for_wifi_disabled(read, *, clock=time.monotonic, sleep=time.sleep):
    """Reuse the M51 10-second stabilization policy, preserving every read.

    read receives its remaining subprocess timeout. Unknown/late/error states
    refuse admission; no settings write or assertion-timeout increase is used.
    """
    start = clock()
    deadline = start + 10.0
    observations = []
    while True:
        remaining = deadline - clock()
        if remaining <= 0:
            raise WifiObservationError("WIFI_STABILIZATION_DEADLINE", observations, clock()-start)
        try:
            value = read(remaining).strip()
        except Exception as exc:
            observations.append({"seconds": clock()-start, "error_type": type(exc).__name__})
            raise WifiObservationError("WIFI_READ_FAILED", observations, clock()-start) from exc
        now = clock()
        observations.append({"seconds": now-start, "value": value})
        if now >= deadline:
            raise WifiObservationError("WIFI_LATE_OBSERVATION", observations, now-start)
        if value == "0":
            return {"status": "PASS", "final_value": "0", "samples": observations,
                    "elapsed_seconds": now-start, "budget_seconds": 10}
        if value != "1":
            raise WifiObservationError("WIFI_UNKNOWN_OBSERVATION", observations, now-start)
        sleep(min(0.25, deadline-now))


def campaign_identity(campaign, source, build_source, run_id, environment):
    if campaign != CAMPAIGN:
        raise ValueError("UNKNOWN_CAMPAIGN")
    if not re.fullmatch(r"[0-9a-f]{40}", source) or source != build_source:
        raise ValueError("SOURCE_MISMATCH")
    if not run_id or not str(run_id).isdigit():
        raise ValueError("RUN_ID_REQUIRED")
    if str(environment.get("GITHUB_RUN_ID", "")) != str(run_id):
        raise ValueError("RUN_ID_MISMATCH")
    if environment.get("GITHUB_SHA") != source:
        raise ValueError("WORKFLOW_SOURCE_MISMATCH")
    if str(environment.get("GITHUB_RUN_ATTEMPT", "")) != "1":
        raise ValueError("AUTOMATIC_OR_MANUAL_RERUN_REFUSED")
    return {"campaign": campaign, "source_commit": source, "run_id": str(run_id),
            "run_attempt": 1, "boot_limit_per_api": 1, "automatic_retry": False}


def classify(*, boot_completed=True, jarvis_installed=False, system_incident=None,
             application_fatal=False, configuration_error=False):
    if not boot_completed or system_incident:
        return "LABORATORY_FAILURE"
    if configuration_error:
        return "CONFIGURATION_FAILURE"
    if jarvis_installed and application_fatal:
        return "APPLICATION_FAILURE_OBSERVED"
    return "UNKNOWN"
