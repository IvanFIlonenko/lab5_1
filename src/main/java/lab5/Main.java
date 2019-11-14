package lab5;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.japi.Pair;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Response;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.asynchttpclient.Dsl.asyncHttpClient;

public class Main {
    private static ActorRef controlActor;

    public static void main(String[] args) throws IOException {
        System.out.println("start app!");
        ActorSystem system = ActorSystem.create("routes");
        final Http http = Http.get(system);
        controlActor = system.actorOf(Props.create(Storage.class));
        final ActorMaterializer materializer =
                ActorMaterializer.create(system);
        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = Flow.of(HttpRequest.class).map(
                request -> {
                    if (request.method() == HttpMethods.GET) {
                        if (request.getUri().path().equals("/")) {
                            String url = request.getUri().query().get("testUrl").orElse("");
                            String reqNum = request.getUri().query().get("count").orElse("");

                            if (url.isEmpty()) {
                                return HttpResponse.create().withEntity(ByteString.fromString("URL is Empty"));
                            }
                            if (reqNum.isEmpty()) {
                                return HttpResponse.create().withEntity(ByteString.fromString("Request number is Empty"));
                            }
                            try {
                                int count = Integer.parseInt(reqNum);
                                Pair<String, Integer> data = new Pair<>(url, count);
                                Source<Pair<String, Integer>, NotUsed> source = Source.from(Collections.singleton(data));
                                Flow<Pair<String, Integer>, HttpResponse, NotUsed> flow = Flow.<Pair<String, Integer>>create()
                                        .map(pair -> new Pair<>(HttpRequest.create().
                                                withUri(pair.first()), pair.second())).
                                                mapAsync(1, pair -> {
                                                    Sink<Long, CompletionStage<Integer>> fold = Sink.fold(0,
                                                            (accumulator, element) -> {
                                                                int responseTime = (int) (0 + element);
                                                                return accumulator + responseTime;
                                                            });
                                                    Future<Object> getResult = Patterns.
                                                            ask(controlActor, new GetDataMsg(new javafx.util.Pair<>(data.first(), data.second())), 5000);
                                                    int value = (int) Await.result(getResult, Duration.create(10, TimeUnit.SECONDS));
                                                    if (value != -1) {
                                                        return CompletableFuture.completedFuture(value);
                                                    }
                                                    return Source.from(Collections.singleton(pair)).
                                                            toMat(Flow.<Pair<HttpRequest, Integer>>create().
                                                                    mapConcat(p -> Collections.nCopies(p.second(), p.first())).
                                                                    mapAsync(1, requestOut -> {
                                                                        CompletableFuture<Long> future = CompletableFuture.supplyAsync(() ->
                                                                                System.currentTimeMillis())
                                                                                .thenCompose(start ->
                                                                                        CompletableFuture.supplyAsync(() -> {
                                                                                            ListenableFuture<Response> whenResponse = asyncHttpClient().
                                                                                                    prepareGet(requestOut.getUri().toString()).execute();
                                                                                            try {
                                                                                                Response response = whenResponse.get();
                                                                                            } catch (InterruptedException | ExecutionException e) {
                                                                                                System.out.println(e);
                                                                                            }
                                                                                            return System.currentTimeMillis() - start;
                                                                                        }));
                                                                        return future;
                                                                    })
                                                                    .toMat(fold, Keep.right()), Keep.right()).run(materializer);
                                                }).map(sum -> {
                                            Patterns.ask(controlActor, new PutDataMsg(new javafx.util.Pair<>(data.first(), new javafx.util.Pair<>(data.second(), sum))), 5000);
                                            Double middleValue = (double) sum / (double) count;
                                            return HttpResponse.create().withEntity(ByteString.fromString(middleValue.toString() + " milliseconds"));
                                        });
                                CompletionStage<HttpResponse> result = source.via(flow).toMat(Sink.last(), Keep.right()).run(materializer);
                                return result.toCompletableFuture().get();
                            } catch (Exception e) {
                                e.printStackTrace();
                                return HttpResponse.create().withEntity(ByteString.fromString(e.toString()));
                            }
                        } else {
                            request.discardEntityBytes(materializer);
                            return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND).withEntity("Wrong Request");
                        }
                    } else {
                        request.discardEntityBytes(materializer);
                        return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND).withEntity("Wrong Request");

                    }
                }
        );
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(
                routeFlow,
                ConnectHttp.toHost("localhost", 8080),
                materializer
        );
        System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
        System.in.read();
        binding
                .thenCompose(ServerBinding::unbind)
                .thenAccept(unbound -> system.terminate());
    }
}
