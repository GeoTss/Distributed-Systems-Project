package com.example.client_efood.Domain;

import java.io.Serializable;

public class Credentials implements Serializable {
    private static final long serialVersionUID = 6L;

    private String username;
    private String password;
    public Credentials(String _username, String _password) {
        this.username = _username;
        this.password = _password;
    }
    public String getUsername() {
        return this.username;
    }
    public String getPassword() {
        return this.password;
    }

}
