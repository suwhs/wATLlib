package su.whs.watl.layout;

import java.util.ArrayList;
import java.util.List;

import su.whs.syllabification.parent.LineBreaker;
import su.whs.watl.text.ContentView;

/**
 * Created by igor n. boulliev <igor@whs.su> on 19.02.17.
 */
public class TextLinesBuilderTest {
    public void add() {
        System.out.printf("TEST!");
        TestFunctionTemplateLinesBuilder();
    }

    class LinesBuilderResult {
        int height;
        List<Line> lines = new ArrayList<Line>();
    }

    private void TestFunctionTemplateLinesBuilder() {
        char text[] = new char[1000];
        Span span = new Span(null,0,1000,text,null);
        span.measure();
        span.mMeasure.mWidth = 10000f;
        span.mMeasure.mWidths = new float[1000];
        for(int i=0; i<1000; i++)
            span.mMeasure.mWidths[i] = 10;
        final LinesBuilderResult result = new LinesBuilderResult();
        TextLinesBuilder builder = new TextLinesBuilder(new TextLinesBuilderCallbacks() {
            @Override
            public int getAvailableWidth() {
                return 100;
            }

            @Override
            public int getAvailableHeight() {
                return -1;
            }

            @Override
            public void onLineHeightChanged(int height) {
                result.height+=height;
            }

            @Override
            public boolean onLineReady(Line textLine) {
                result.lines.add(textLine);
                return true;
            }

            @Override
            public boolean isWhitespacesCompressEnabled() {
                return false;
            }

//            @Override
//            public void onSpanProcessingFinished(Span span) {
//                if (result.lines.size()!=100)
//                    throw new RuntimeException("test failed");
//                for (Line line :result.lines) {
//
//                }
//            }
        },makeTestOptions(mockLineBreaker()));
        builder.add(text,span,null,null);
    }

    ContentView.Options makeTestOptions(LineBreaker lineBreaker) {
        ContentView.Options result = new ContentView.Options();
        result.setLineBreaker(lineBreaker);
        return result;
    }

    LineBreaker mockLineBreaker() {
        return new LineBreaker() {
            @Override
            public int nearestLineBreak(char[] text, int start, int _end, int limit) {
                return start;
            }
        };
    }

    LineBreaker mockHyphenBreaker(final char[] map) {
        return new LineBreaker() {
            @Override
            public int nearestLineBreak(char[] text, int start, int _end, int limit) {
                for(;start>-1;start--) {
                    if (map[start] == '-') return start;
                }
                return 0;
            }
        };
    }
}