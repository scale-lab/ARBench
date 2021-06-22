package benchmark.benchmark;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import benchmark.helloar.HelloArActivity;

public class BenchmarkActivity extends AppCompatActivity {
    public static final String FILE_NUMBER = "benchmark.FILE_NUMBER";
    LinearLayout resultsDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        resultsDisplay = (LinearLayout) findViewById(R.id.results_display);
    }

    public void onStartBenchmark(View view) {
        Intent intent = new Intent(this, HelloArActivity.class);
        intent.putExtra(FILE_NUMBER, 1);
        startActivityForResult(intent, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        String logPath = "/storage/emulated/0/Android/data/benchmark.helloar/files/fps.csv";
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_CANCELED) {
            new AlertDialog.Builder(this).setMessage("Failed to perform test " + requestCode).show();
        } else {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(logPath));
                String line = reader.readLine();
                int currentPhase = 1;
                long startTime=Long.decode(line.split(",")[1]), t=0;
                long update=0, maxInput=0, renderBackground=0, renderObjects=0, total=0;
                int i = 0;
                while(true) {
                    if (line != null) {
                        String[] times = line.split(",");
                        int phase = Integer.decode(times[0]);
                        if (phase != currentPhase) {
                            float fps = 1000.f*i/(t - startTime);
                            TextView results = new TextView(this);
                            results.setText(
                                    "FPS and Runtimes - Test " + requestCode + " Phase " + currentPhase + "\n"
                                    + "FPS: " + fps + "\n"
                                            + "Update: " + (float)update/i + "\n"
                                            + "Max Input: " + maxInput + "\n"
                                            + "Render Background: " + (float)renderBackground/i + "\n"
                                            + "Render Objects: " + (float)renderObjects/i + "\n"
                                            + "Total Runtime: " + (float)total/i + "\n");
                            resultsDisplay.addView(results);
                            startTime = Long.decode(times[1]);
                            currentPhase = phase;
                            update = 0;
                            maxInput = 0;
                            renderBackground = 0;
                            renderObjects = 0;
                            total = 0;
                            i = 0;
                        }
                        t = Long.decode(times[1]);
                        update += Integer.decode(times[2]);
                        maxInput = Math.max(maxInput, Integer.decode(times[3]));
                        renderBackground += Integer.decode(times[4]);
                        renderObjects += Integer.decode(times[5]);
                        total += Integer.decode(times[6]);
                        i++;
                    } else {
                        float fps = 1000.f*i/(t - startTime);
                        TextView results = new TextView(this);
                        results.setText(
                                "FPS and Runtimes - Test " + requestCode + " Phase " + currentPhase + "\n"
                                        + "FPS: " + fps + "\n"
                                        + "Update: " + (float)update/i + "\n"
                                        + "Max Input: " + maxInput + "\n"
                                        + "Render Background: " + (float)renderBackground/i + "\n"
                                        + "Render Objects: " + (float)renderObjects/i + "\n"
                                        + "Total Runtime: " + (float)total/i + "\n");
                        resultsDisplay.addView(results);
                        break;
                    }
                    line = reader.readLine();
                }
            } catch (FileNotFoundException e) {
                new AlertDialog.Builder(this).setMessage("Failed to perform test " + requestCode).show();
            } catch (IOException e) {
                new AlertDialog.Builder(this).setMessage("Failed to perform test " + requestCode).show();
            } catch (Exception e) {
                new AlertDialog.Builder(this).setMessage(e.getMessage()).show();
            }
        }

        if (requestCode <= 3) {
//            Intent intent = new Intent(this, HelloArActivity.class);
//            intent.putExtra(FILE_NUMBER, requestCode + 1);
//            startActivityForResult(intent, requestCode + 1);
        }
    }

}
