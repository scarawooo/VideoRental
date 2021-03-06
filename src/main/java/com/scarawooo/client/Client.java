package com.scarawooo.client;

import com.scarawooo.client.view.dialogs.*;
import com.scarawooo.dto.ReserveUnitDTO;
import com.scarawooo.dto.WarehouseUnitDTO;
import com.scarawooo.service.Notification;
import com.scarawooo.client.view.Content;
import javafx.util.Pair;

import javax.swing.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Exchanger;

import static com.scarawooo.service.ConnectionProperties.PORT;

public class Client {
    private static Client access = new Client();
    private Content content = new Content();
    private Exchanger<Notification> exchanger = new Exchanger<>();
    private ArrayList<ReserveUnitDTO> reserves;

    private Client() {
        new Thread(this::connection).start();
    }

    private void connection() {
        try (Socket socket = new Socket("127.0.0.1", PORT);
             ObjectOutputStream socketObjectWriter = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream socketObjectReader = new ObjectInputStream(socket.getInputStream())) {
            Notification request;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    request = exchanger.exchange(Notification.DEFAULT);
                    socketObjectWriter.writeObject(request);
                    switch (request) {
                        case AUTHORIZATION:
                            socketObjectWriter.writeObject(LoginDialog.getLoginData());
                            if (socketObjectReader.readObject() == Notification.SUCCESSFULLY) {
                                SwingUtilities.invokeLater(() -> {
                                    content.getReserves().getModel().clear();
                                    content.updateTitle();
                                });
                                JOptionPane.showMessageDialog(null, "Авторизация прошла успешно",
                                        "", JOptionPane.INFORMATION_MESSAGE);
                            } else {
                                JOptionPane.showMessageDialog(null,
                                        "Неверный логин и/или пароль",
                                        "Ошибка авторизации", JOptionPane.ERROR_MESSAGE);

                            }
                            break;
                        case REGISTRATION:
                            socketObjectWriter.writeObject(RegistrationDialog.getRegData());
                            if (socketObjectReader.readObject() == Notification.SUCCESSFULLY) {
                                JOptionPane.showMessageDialog(null, "Регистрация прошла успешно",
                                        "", JOptionPane.INFORMATION_MESSAGE);
                                break;
                            } else JOptionPane.showMessageDialog(null,
                                    "Введены некорректные данные при регистрации",
                                    "Ошибка регистрации", JOptionPane.ERROR_MESSAGE);
                            break;
                        case RESERVE:
                            if (socketObjectReader.readObject() == Notification.UNAUTHORIZED) {
                                JOptionPane.showMessageDialog(null,
                                        "Вы не авторизовались",
                                        "Ошибка бронирования", JOptionPane.ERROR_MESSAGE);
                            }
                            else {
                                socketObjectWriter.writeObject(reserves);
                                List reserveItemsAmount = (List) socketObjectReader.readObject();
                                StringBuilder stringBuilder = new StringBuilder();
                                for (int i = 0; i < reserves.size(); ++i)
                                    stringBuilder.append(reserves.get(i).getInfo().concat("\nДоступно для бронирования: " + ((Pair) reserveItemsAmount.get(i)).getValue()));
                                int choice = JOptionPane.showConfirmDialog(null, stringBuilder,
                                        "Оформление брони", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
                                if (choice == JOptionPane.YES_OPTION) {
                                    socketObjectWriter.writeObject(Notification.AGREE);
                                    SwingUtilities.invokeLater(() -> content.getBasket().getModel().clear());
                                    if (socketObjectReader.readObject() == Notification.SUCCESSFULLY) {
                                        JOptionPane.showMessageDialog(null, "Бронирование успешно завершено",
                                                "", JOptionPane.INFORMATION_MESSAGE);
                                        break;
                                    } else JOptionPane.showMessageDialog(null,
                                            "При бронировании произошла ошибка. Бронирование отменено",
                                            "Ошибка бронирования", JOptionPane.ERROR_MESSAGE);
                                } else socketObjectWriter.writeObject(Notification.DISAGREE);
                            }
                            break;
                        case UNRESERVE:
                            if (socketObjectReader.readObject() == Notification.UNAUTHORIZED) {
                                JOptionPane.showMessageDialog(null,
                                        "Вы не авторизовались",
                                        "Ошибка бронирования", JOptionPane.ERROR_MESSAGE);
                            }
                            else {
                                socketObjectWriter.writeObject(content.getReserves().getReservesList().getSelectedValue());
                                if (socketObjectReader.readObject() == Notification.SUCCESSFULLY) {
                                    SwingUtilities.invokeLater(() -> {
                                        for (int j = 0; j < content.getGoods().getModel().size(); ++j)
                                            if (content.getGoods().getModel().get(j).equals(content.getReserves().getReservesList().getSelectedValue().getWarehouseUnit())) {
                                                content.getGoods().getModel().get(j).setAmount(content.getGoods().getModel().get(j).getAmount() + content.getReserves().getReservesList().getSelectedValue().getAmount());
                                                break;
                                            }
                                        content.getReserves().getModel().remove(content.getReserves().getReservesList().getSelectedIndex());
                                    });
                                    JOptionPane.showMessageDialog(null, "Бронь аннулирована",
                                            "", JOptionPane.INFORMATION_MESSAGE);
                                } else JOptionPane.showMessageDialog(null,
                                        "Ошибка аннулирования брони",
                                        "Ошибка аннулирования брони", JOptionPane.ERROR_MESSAGE);
                            }
                            break;
                        case GET_GOODS:
                            final List goodsList = (List) socketObjectReader.readObject();
                            SwingUtilities.invokeLater(() -> {
                                DefaultListModel<WarehouseUnitDTO> goodsModel = content.getGoods().getModel();
                                DefaultListModel<WarehouseUnitDTO> basketModel = content.getBasket().getModel();
                                goodsModel.clear();
                                for (Object i : goodsList)
                                    goodsModel.addElement((WarehouseUnitDTO) i);
                                for (int i = 0; i < basketModel.size(); ++i)
                                    for (int j = 0; j < goodsModel.size(); ++j)
                                        if (basketModel.get(i).equals(goodsModel.get(j)))
                                            if (basketModel.get(i).getAmount() > goodsModel.get(j).getAmount())
                                                goodsModel.get(j).setAmount(0);
                                            else
                                                goodsModel.get(j).setAmount(goodsModel.get(j).getAmount() - basketModel.get(i).getAmount());
                            });
                            break;
                        case GET_RESERVES:
                            if (socketObjectReader.readObject() == Notification.UNAUTHORIZED) {
                                JOptionPane.showMessageDialog(null,
                                        "Вы не авторизовались",
                                        "Ошибка получения списка бронирований", JOptionPane.ERROR_MESSAGE);
                            }
                            else {
                                final List reservesList = (List) socketObjectReader.readObject();
                                SwingUtilities.invokeLater(() -> {
                                    DefaultListModel<ReserveUnitDTO> reservesModel = content.getReserves().getModel();
                                    reservesModel.clear();
                                    for (Object i : reservesList)
                                        reservesModel.addElement((ReserveUnitDTO) i);
                                });
                            }
                            break;
                        case DISCONNECT:
                            Thread.currentThread().interrupt();
                    }
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (IOException | ClassNotFoundException exception) {
            System.out.println("Связь с сервером потеряна " + exception);
        }
    }

    public Exchanger<Notification> getExchanger() {
        return exchanger;
    }

    public Content getContent() {
        return content;
    }

    public void setReserves(ArrayList<ReserveUnitDTO> reserves) {
        this.reserves = reserves;
    }

    public static void main(String[] args) {}

    public static Client getAccess() {
        return access;
    }
}
