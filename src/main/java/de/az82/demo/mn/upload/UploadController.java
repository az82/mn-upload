package de.az82.demo.mn.upload;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.micronaut.http.MediaType.MULTIPART_FORM_DATA;
import static java.lang.System.arraycopy;

@Controller("/upload")
public class UploadController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UploadController.class);
    private Path dir;

    private static byte[] compactBuffer(List<byte[]> buffer) {
        int size = 0;
        for (byte[] bytes : buffer) {
            size += bytes.length;
        }

        byte[] result = new byte[size];

        int i = 0;
        for (byte[] bytes : buffer) {
            arraycopy(bytes, 0, result, i, bytes.length);
            i += bytes.length;
        }

        return result;
    }

    @PostConstruct
    public void postConstruct() throws IOException {
        dir = Files.createTempDirectory("mn-upload");
    }

    @Get()
    public HttpResponse<Object> index() {
        return HttpResponse.ok("Use a POST request");
    }


    @Post(consumes = MULTIPART_FORM_DATA, produces = APPLICATION_JSON)
    public Single<? extends HttpResponse<Object>> index(Flowable<StreamingFileUpload> file) {
        LOGGER.info("Uploads started");

        return file
                .flatMap(upload -> {
                    String filename = upload.getFilename();
                    Path path = dir.resolve(filename);
                    LOGGER.info("Starting upload of {} to {}", filename, path);

                    // This blocks the event loop
                    // Should be done asynchronously and synchronized with below
                    // For instance with a onSubscribe handler on the Flowable
                    // below
                    // Will complicate the code somewhat
                    FileOutputStream outputStream = new FileOutputStream(path.toFile());

                    return Flowable.fromPublisher(upload)
                            // Must be on the default scheduler as getBytes
                            // is not thread safe - damn Netty
                            // Also does not work when the entire method is
                            // blocking and on the IO scheduler - argh
                            .map(PartData::getBytes)

                            // Assemble larger chunks for serialization
                            // Bigger chunks are way better for laggy IO
                            .buffer(128) // 128*64kb=1MB
                            .map(UploadController::compactBuffer)

                            // Do the write on the IO thread pool
                            // Use a bounded pool here or else OOM
                            .observeOn(Schedulers.single())

                            // Write to output stream (blocking)
                            .map(bytes -> {
                                LOGGER.debug("Write {} bytes", bytes.length);
                                // Make it really slow to identify issues with backpressure
                                Thread.sleep(ThreadLocalRandom.current().nextLong(500, 1000));
                                outputStream.write(bytes);

                                return bytes.length;
                            })
                            // Logging
                            .collect(() -> new int[1], (sum, length) -> sum[0] += length)
                            .doAfterSuccess((sum) -> LOGGER.info("Upload done {}:{}", filename, sum[0]))

                            // Response & Error handling
                            .map((length) -> HttpResponse.ok())
                            .onErrorReturn((e) -> {
                                LOGGER.error("Exception", e);
                                return HttpResponse.serverError(e);
                            })
                            .doFinally(outputStream::close)
                            .toFlowable();
                })
                // Collect responses
                .collect(HttpResponse::ok, (result, response) -> {
                    if (response.status() != HttpStatus.OK) {
                        result.status(response.status());
                        result.body(response.body());
                    }
                })
                .doOnSuccess((x) -> LOGGER.info("All uploads done"));
    }


}