package sample;

import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortMessageListenerWithExceptions;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.converter.FormatStringConverter;
import javafx.scene.control.*;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.util.List;


public class Main extends Application {
    public static void main(String[] args){launch (args);}
    public static TableView<XYChart.Data<Number, Number>> getTableView() {
        var table = new TableView<XYChart.Data<Number, Number>> ();
        var time = new TableColumn<XYChart.Data<Number, Number>, Number>("X");
        time.setCellValueFactory(row -> row.getValue().XValueProperty());
        var dateFormat = DateFormat.getTimeInstance();
        var converter = new FormatStringConverter<Number>(dateFormat);
        time.setCellFactory(column -> new TextFieldTableCell<>(converter));
        var value = new TableColumn<XYChart.Data<Number, Number>,Number>("Y");
        value.setCellValueFactory(row -> row.getValue().YValueProperty());
        table.getColumns().setAll(List.of(time,value));
        return table;
    }

    @Override
    public void start (Stage primaryStage) throws IOException {
        var controller = new dataController(OutputStream.nullOutputStream());
        long timeStart = System.currentTimeMillis();


        var sp = SerialPortService.getSerialPort("/dev/cu.usbserial-0001");
        var outputStream = sp.getOutputStream();
        sp.addDataListener(controller);


        var table = getTableView();
        table.setItems(controller.getDataPoints());
        var vbox = new VBox(table);

        primaryStage.setTitle("Automatic Watering System");

        var pane = new BorderPane();

        var xAxis = new NumberAxis("time elapsed (Seconds)",  timeStart, timeStart + 100000,10000);
        var yAxis = new NumberAxis("Voltage", 300, 200, 700);

        var series = new XYChart.Series<>(controller.getDataPoints());

        var lineChart = new LineChart<>(xAxis, yAxis , FXCollections.singletonObservableList(series));

        lineChart.setTitle("Voltage from Moisture sensor");


        var button = new Button("Pump");

        button.setOnMousePressed(value -> {
            try{
                outputStream.write(255);
            }
            catch(IOException e){
                e.printStackTrace();

            }
            button.setText("Pumping");

        });
        button.setOnMousePressed(value ->{
            try{
                outputStream.write(1);

            }
            catch(IOException e){
                e.printStackTrace();
            }
            button.setText("Pump");
        });

        var slider = new Slider();
        slider.setMin(0.0);
        slider.setMax(100.0);

    var label = new Label();
    var hbox = new HBox(slider, label);
    hbox.setSpacing(10.0);
    label.setText("Initiate the Buzzer State");
    slider.valueProperty().addListener((observableValue, oldValue, newValue) -> {


        if(newValue.intValue() >= 50){
            String sliderOn = "ON";

            try{
                label.setText("Buzzer State:" + sliderOn);
                outputStream.write(100);

            }catch (IOException e){
                e.printStackTrace();
            }
        }
        else if (newValue.intValue() < 50) {
            String sliderOff = "OFF";
            label.setText("Buzzer state:" + sliderOff);
            try{
                outputStream.write(2);
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            System.out.println("Something is wrong");

        }

    });
    pane.setLeft(lineChart);
    pane.setPadding(new Insets(0,55,0,55));
    pane.setTop(hbox);
    pane.setBottom(button);
    pane.setCenter(table);

    var scene = new Scene(pane, 870, 5500);
    primaryStage.setScene(scene);
    primaryStage.show();




    }

    public static class dataController implements SerialPortMessageListenerWithExceptions {
        private static final byte[] DELIMITER = new byte[]{'\n'};
        private final ObservableList<XYChart.Data<Number, Number>> dataPoints;
        private final OutputStream outputstream;

        public dataController(OutputStream outputStream)  {
            this.outputstream = outputStream;
            this.dataPoints = FXCollections.observableArrayList();

        }
        public ObservableList<XYChart.Data<Number, Number>> getDataPoints() {
            return dataPoints;

        }

        @Override
        public int getListeningEvents() {
            return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
        }

        @Override
        public void serialEvent(SerialPortEvent serialPortEvent) {

            if (serialPortEvent.getEventType() != SerialPort.LISTENING_EVENT_DATA_RECEIVED){
                return;
            }

            var data = serialPortEvent.getReceivedData();


            Number dataInt = ByteBuffer.wrap(data).getInt();

            int newData = (int)dataInt;

            Number time = System.currentTimeMillis();
            var dataPoint = new XYChart.Data<>(time, dataInt);

            Platform.runLater(() -> this.dataPoints.add(dataPoint));
        }


        @Override
        public void catchException(Exception e){e.printStackTrace();}

        @Override
        public byte[] getMessageDelimiter(){return DELIMITER;}

        @Override
        public boolean delimiterIndicatesEndOfMessage(){return true;}
    }

    public static class SerialPortService {

        public SerialPortService(){}

        public static SerialPort getSerialPort(String portDescriptor){
            var sp = SerialPort.getCommPort(portDescriptor);
            sp.setComPortParameters(9600,Byte.SIZE, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
            sp.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 0,0);

            var hasOpened = sp.openPort();
            if (!hasOpened) {
                throw new IllegalStateException("Failed to open port");
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {sp.closePort();}));
            return sp;
        }
    }
}
