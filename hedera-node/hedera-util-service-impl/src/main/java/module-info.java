import com.hedera.node.app.service.util.impl.UtilServiceImpl;

module com.hedera.node.app.service.util.impl {
	requires com.hedera.node.app.service.util;
	requires com.hedera.hashgraph.protobuf.java.api;
	requires static com.github.spotbugs.annotations;

	provides com.hedera.node.app.service.util.UtilService with
			UtilServiceImpl;

	exports com.hedera.node.app.service.util.impl to
			com.hedera.node.app.service.util.impl.test;
	exports com.hedera.node.app.service.util.impl.handlers;
}
