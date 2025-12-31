package log.charter.io.rsc.xml;

import java.io.File;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;

import log.charter.data.ChordLibrary;
import log.charter.data.song.ChordTemplate;
import log.charter.io.XMLHandler;
import log.charter.util.RW;
import log.charter.util.collections.ArrayList2;

public class ChordLibraryXStreamHandler {
	private static XStream xstream = prepareXStream();

	private static XStream prepareXStream() {
		final XStream xstream = new XStream();
		xstream.registerConverter(new CollectionConverter(xstream.getMapper(), ArrayList2.class));
		xstream.ignoreUnknownElements();
		xstream.processAnnotations(ChordLibrary.class);
		xstream.processAnnotations(ChordTemplate.class);
		xstream.alias("chordLibrary", ChordLibrary.class);
		xstream.alias("chord", ChordTemplate.class);
		xstream.allowTypes(new Class[] { //
				ChordLibrary.class, //
				ChordTemplate.class //
		});

		return xstream;
	}

	public static ChordLibrary readChordLibrary(final File file) {
		final String content = RW.read(file, "UTF-8");
		if (content == null || content.isEmpty()) {
			return null;
		}

		return (ChordLibrary) xstream.fromXML(content);
	}

	public static void writeChordLibrary(final ChordLibrary library, final File file) {
		final String xml = XMLHandler.generateXML(xstream, library);
		RW.write(file, xml, "UTF-8");
	}
}
