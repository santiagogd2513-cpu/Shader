import zipfile, sys
src, dst = sys.argv[1], sys.argv[2]
zin = zipfile.ZipFile(src)
with zipfile.ZipFile(dst, 'w') as zout:
    for i in zin.infolist():
        data = zin.read(i.filename)
        n = zipfile.ZipInfo(i.filename, date_time=i.date_time)
        n.compress_type = i.compress_type
        n.external_attr = i.external_attr
        n.create_system = i.create_system
        if i.compress_type == zipfile.ZIP_STORED:
            align = 4096 if i.filename.endswith('.so') else 4
            off = zout.fp.tell() + 30 + len(i.filename.encode())
            pad = (-off) % align
            if pad and pad < 4: pad += align  # extra field needs >=4 bytes for header
            n.extra = (b'\x35\xd9' + (pad-4).to_bytes(2,'little') + b'\0'*(pad-4)) if pad else b''
        zout.writestr(n, data)
