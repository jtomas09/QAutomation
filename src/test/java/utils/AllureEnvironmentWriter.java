package utils;
import io.appium.java_client.AppiumDriver;
import java.io.File;
import java.io.FileWriter;

public class AllureEnvironmentWriter {
    public static void crearEnvironmentProperties(AppiumDriver driver) {
        try {
            File file = new File("build/allure-results/environment.properties");
            file.getParentFile().mkdirs();
            FileWriter writer = new FileWriter(file);

            writer.write("Proyecto=Automatizacion Cinepolis\n");
            writer.write("Tester Automatizador=Jairo\n");
            writer.write("Device Name=" + driver.getCapabilities().getCapability("deviceName") + "\n");
            writer.write("Platform Name=" + driver.getCapabilities().getCapability("platformName") + "\n");
            writer.write("Platform Version=" + driver.getCapabilities().getCapability("platformVersion") + "\n");
            writer.write("SO Ejecucion=" + System.getProperty("os.name") + "\n");
            writer.write("Java Version=" + System.getProperty("java.version") + "\n");


            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
