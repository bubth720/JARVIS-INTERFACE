"""Refuse silently clamped emulator geometry before installing any laboratory APK."""
import re

def require_display(size_report, density_report, requested_size, requested_density):
    def effective(report, label, pattern):
        physical=re.findall(r'^Physical '+label+r':\s*('+pattern+r')\s*$',report,re.M)
        override=re.findall(r'^Override '+label+r':\s*('+pattern+r')\s*$',report,re.M)
        if len(physical)!=1 or len(override)>1:raise ValueError('DISPLAY_OBSERVATION_UNKNOWN')
        return override[0] if override else physical[0]
    actual_size=effective(size_report,'size',r'\d+x\d+')
    actual_density=effective(density_report,'density',r'\d+')
    if actual_size!=requested_size or actual_density!=str(requested_density):
        raise ValueError('DISPLAY_CONFIGURATION_CLAMPED_OR_MISMATCHED:'+actual_size+':'+actual_density)
    return {'effective_size':actual_size,'effective_density':int(actual_density)}
