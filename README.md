Demonstrator to evaluate proper handling of streaming file uploads with 
Micronaut.

To test with a large file run the following commands:

1st console:

```bash
./gradlew run 
```

2nd console:

```bash
dd if=/dev/zero of=1gb.bin bs=1GB count=1
dd if=/dev/zero of=10gb.bin bs=1GB count=10
curl -v -F "file=@1gb.bin" -F "file=@10gb.bin" http://localhost:8080/upload
```

The controller will save large files in a temporary location, see logs.