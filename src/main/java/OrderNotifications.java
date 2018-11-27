import com.messagebird.MessageBirdClient;
import com.messagebird.MessageBirdService;
import com.messagebird.MessageBirdServiceImpl;
import com.messagebird.exceptions.GeneralException;
import com.messagebird.exceptions.UnauthorizedException;
import com.messagebird.objects.MessageResponse;
import io.github.cdimascio.dotenv.Dotenv;
import spark.ModelAndView;
import spark.template.mustache.MustacheTemplateEngine;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;

public class OrderNotifications {
    public static String nextStatus(String status) {
        switch (status) {
            case "pending":
                return "confirmed";
            case "readyForDelivery":
                return "delivered";
            case "confirmed":
                return "delayed";
        }
        return "";
    }

    public static String submitText(String status) {
        switch (status) {
            case "pending":
                return "confirm order";
            case "readyForDelivery":
                return "mark delivered";
            case "confirmed":
                return "mark delayed";
        }
        return "";
    }

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();

        // Create a MessageBirdService
        final MessageBirdService messageBirdService = new MessageBirdServiceImpl(dotenv.get("MESSAGEBIRD_API_KEY"));
        // Add the service to the client
        final MessageBirdClient messageBirdClient = new MessageBirdClient(messageBirdService);

        // Set up Order "Database"
        List<Map<String, String>> orderDatabase = new ArrayList<Map<String, String>>();

        Map<String, String> order = new HashMap<>();

        order.put("name", "Hannah Hungry");
        order.put("phone", "+319876543210"); // <- put your number here for testing
        order.put("items", "1 x Hipster Burger + Fries");
        order.put("status", "pending");
        order.put("nextStatus", nextStatus("pending"));
        order.put("submitText", submitText("pending"));
        order.put("index", "0");

        orderDatabase.add(order);

        order = new HashMap<>();
        order.put("name", "Mike Madeater");
        order.put("phone", "+319876543211"); // <- put your number here for testing
        order.put("items", "1 x Chef Special Mozzarella Pizza");
        order.put("status", "pending");
        order.put("nextStatus", nextStatus("pending"));
        order.put("submitText", submitText("pending"));
        order.put("index", "1");

        orderDatabase.add(order);

        get("/",
                (req, res) ->
                {
                    Map<String, Object> model = new HashMap<>();
                    model.put("orders", orderDatabase);

                    return new ModelAndView(model, "orders.mustache");
                },

                new MustacheTemplateEngine()
        );

        post("/updateOrder",
                (req, res) ->
                {
                    // Read request
                    Integer id = Integer.parseInt(req.queryParams("id"));
                    // Get order
                    final Map<String, String> givenOrder = orderDatabase.get(id);

                    Map<String, Object> model = new HashMap<>();

                    try {
                        if (!givenOrder.isEmpty()) {
                            // Update order
                            givenOrder.put("status", req.queryParams("status"));

                            givenOrder.put("nextStatus", nextStatus(givenOrder.get("status")));
                            givenOrder.put("submitText", submitText(givenOrder.get("status")));

                            model.put("orders", orderDatabase);

                            String body = "";

                            // Compose a message, based on current status
                            switch (givenOrder.get("status")) {
                                case "confirmed":
                                    body = String.format("%s, thanks for ordering at OmNomNom Foods! We are now preparing your food with love and fresh ingredients and will keep you updated.", givenOrder.get("name"));
                                case "delayed":
                                    body = String.format("%s, sometimes good things take time! Unfortunately your order is slightly delayed but will be delivered as soon as possible.", givenOrder.get("name"));
                                case "delivered":
                                    body = String.format("%s, you can start setting the table! Our driver is on their way with your order! Bon appetit!", givenOrder.get("name"));
                            }

                            // convert String number into acceptable format
                            BigInteger phoneNumber = new BigInteger(givenOrder.get("phone"));
                            final List<BigInteger> phones = new ArrayList<BigInteger>();
                            phones.add(phoneNumber);

                            final MessageResponse response = messageBirdClient.sendMessage("NomNom", body, phones);
                            return new ModelAndView(model, "orders.mustache");
                        } else {
                            model.put("errors", "Invalid input!");
                            return new ModelAndView(model, "orders.mustache");
                        }
                    } catch (UnauthorizedException | GeneralException ex) {
                        model.put("errors", ex.toString());
                        return new ModelAndView(model, "orders.mustache");
                    }
                },
                new MustacheTemplateEngine()
        );
    }
}