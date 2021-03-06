package tokyo.boblennon.nuwe.jump2digital.application.handler;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.net.URI;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tokyo.boblennon.nuwe.jump2digital.domain.product.Product;
import tokyo.boblennon.nuwe.jump2digital.infrastructure.product.ProductRepositoryImp;

@Component
public class ProductHandler {

    private final Validator validator;
    private final ProductRepositoryImp productRepositoryImp;

    @Autowired
    public ProductHandler(final ProductRepositoryImp productRepositoryImp, final Validator validator) {
        this.productRepositoryImp = productRepositoryImp;
        this.validator = validator;
    }

    public Mono<ServerResponse> add(ServerRequest request) {
        Mono<Product> product = request.bodyToMono(Product.class);

        return product.flatMap(p -> {
            p.setId(UUID.randomUUID());
            Errors errs = new BeanPropertyBindingResult(p, Product.class.getName());
            this.validator.validate(p, errs);
            if (errs.hasErrors()) {
                return Flux.fromIterable(errs.getFieldErrors())
                        .map(err -> "Field " + err.getField() + " " + err.getDefaultMessage())
                        .collectList()
                        .flatMap(list -> ServerResponse.badRequest().bodyValue(list));
            } else {
                return this.productRepositoryImp.add(p)
                        .flatMap(pData -> ServerResponse.created(URI
                                .create("/product"))
                                .contentType(APPLICATION_JSON)
                                .bodyValue(pData));
            }
        });
    }

    public Mono<ServerResponse> findById(ServerRequest request) {
        UUID id = UUID.fromString(request.pathVariable("id"));

        return this.productRepositoryImp.findById(id).flatMap(p -> ServerResponse
                .ok()
                .contentType(APPLICATION_JSON)
                .bodyValue(p))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> update(ServerRequest request) {
        Mono<Product> product = request.bodyToMono(Product.class);
        UUID id = UUID.fromString(request.pathVariable("id"));

        Mono<Product> productData = this.productRepositoryImp.findById(id);

        return productData.zipWith(product, (db, req) -> {
            db.setName(req.getName());
            db.setPrice(req.getPrice());
            db.setDesc(req.getDesc());
            return db;
        })
                .flatMap(p -> ServerResponse.created(URI
                        .create("/product" + p.getId()))
                        .body(this.productRepositoryImp.add(p), Product.class))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> delete(ServerRequest request) {
        UUID id = UUID.fromString(request.pathVariable("id"));

        return this.productRepositoryImp.findById(id).flatMap(p -> this.productRepositoryImp.delete(p)
                .then(ServerResponse.noContent().build()))
                .switchIfEmpty(ServerResponse.notFound().build());
    }
}
