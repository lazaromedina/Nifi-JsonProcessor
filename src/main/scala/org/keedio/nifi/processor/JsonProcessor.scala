package org.keedio.nifi.processor

import java.io.{IOException, InputStream, OutputStream}
import java.util.concurrent.atomic.AtomicReference

import com.jayway.jsonpath.JsonPath
import org.apache.commons.io.IOUtils
import org.apache.nifi.annotation.documentation.{CapabilityDescription, Tags}
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.logging.ProcessorLog
import org.apache.nifi.processor._
import org.apache.nifi.processor.exception.ProcessException
import org.apache.nifi.processor.io.{InputStreamCallback, OutputStreamCallback}
import org.apache.nifi.processor.util.StandardValidators

import scala.collection.immutable.HashSet


/**
 * Created by luislazaro on 21/9/15.
 * lalazaro@keedio.com
 * Keedio
 */

@Tags("Json", "JsonProcessor")
@CapabilityDescription("Fetch value from json path")
class JsonProcessor extends AbstractProcessor {

    private val properties: List[PropertyDescriptor] = Nil
    private val relationships: HashSet[Relationship] = HashSet.empty

    /**
     * Function init called at the start of Apache Nifi
     * @param context
     */
    override def init(context: ProcessorInitializationContext): Unit = {
        JsonProcessor.JSON_PATH :: properties
        relationships.+(JsonProcessor.SUCCESS)
    }

    /**
     * Function onTrigger called wheneve a flowFile is passed to the processor.
     * @param context
     * @param session
     */
    @throws(classOf[ProcessException])
    override def onTrigger(context: ProcessContext, session: ProcessSession): Unit = {
        val log: ProcessorLog = this.getLogger
        val value: AtomicReference[String] = new AtomicReference[String]()
        var flowFile: FlowFile = session.get()

        session.read(flowFile, new InputStreamCallback {
            @throws(classOf[IOException])
            override def process(inputStream: InputStream): Unit = {
                try {
                    val json = IOUtils.toString(inputStream)
                    val result = JsonPath.read(json, "$.hello")
                    value.set(result)
                } catch {
                    case ex: IOException => ex.printStackTrace()
                        log.error("Failed to read json string")
                }
            }
        })
        val results: String = value.get()
        results.nonEmpty && results.isInstanceOf[NotNull] match {
            case true => flowFile = session.putAttribute(flowFile, "match", results)
            case false => println("error")
        }

        flowFile = session.write(flowFile, new OutputStreamCallback {
            @throws(classOf[IOException])
            override def process(outputStream: OutputStream): Unit =
                outputStream.write(value.get().getBytes())
        })
        session.transfer(flowFile, JsonProcessor.SUCCESS)

    }

    override def getRelationships(): HashSet[Relationship] = relationships

    override def getSupportedPropertyDescriptors(): List[PropertyDescriptor] = properties
}

object JsonProcessor {
    final val MATCH_ATTR: String = "match"
    final val JSON_PATH: PropertyDescriptor = new org.apache.nifi.components.PropertyDescriptor.Builder.type
        .name("Json Path")
        .required(true)
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .build()

    final val SUCCESS: Relationship = new org.apache.nifi.processor.Relationship.Builder.type
        .name("SUCCESS")
        .description("Success relationship")
        .build()

}
