#!/usr/bin/env python3
"""Create the cloud-init NoCloud seed ISO (label CIDATA).
usage: python make-seed.py path\\to\\id_ed25519.pub   (pip install pycdlib)"""
import io, sys, pathlib, pycdlib
here = pathlib.Path(__file__).parent
key = pathlib.Path(sys.argv[1]).read_text().strip()
files = {
    "user-data": (here / "user-data.template").read_text().replace("__SSH_PUBLIC_KEY__", key),
    "meta-data": "instance-id: firevm-1\nlocal-hostname: firevm\n",
}
iso = pycdlib.PyCdlib(); iso.new(interchange_level=3, joliet=3, rock_ridge="1.09", vol_ident="CIDATA")
for name, text in files.items():
    data = text.encode()
    iso.add_fp(io.BytesIO(data), len(data), "/" + name.upper().replace("-", "") + ".;1", rr_name=name, joliet_path="/" + name)
iso.write(str(here / "seed.iso")); iso.close()
print("seed.iso created")
