package com.example.calc

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import java.lang.NumberFormatException

class MainActivity : AppCompatActivity(), View.OnClickListener, View.OnLongClickListener {

    private val DIVIDE = 10
    private val MULTIPLY = 11
    private val SUBTRACT = 12
    private val PLUS = 13
//    private val NEGATIVE = 14
//    private val DOT = 15
//    private val CLEAR = 16
//    private val EQUALS = 17
    private val NONE = -1
    private var currentOperator = NONE
    private lateinit var button0: Button
    private lateinit var button1: Button
    private lateinit var button2: Button
    private lateinit var button3: Button
    private lateinit var button4: Button
    private lateinit var button5: Button
    private lateinit var button6: Button
    private lateinit var button7: Button
    private lateinit var button8: Button
    private lateinit var button9: Button
    private lateinit var buttonPlus: Button
    private lateinit var buttonSubtract: Button
    private lateinit var buttonDivide: Button
    private lateinit var buttonMultiply: Button
    private lateinit var buttonEquals: Button
    private lateinit var buttonClear: Button
    private lateinit var buttonDot: Button
    private lateinit var buttonBackspace: Button
    private lateinit var display: TextView
    private var text = ""
    private var operators = CharArray(4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        display = findViewById(R.id.display)
        button0 = findViewById(R.id.button0)
        button1 = findViewById(R.id.button1)
        button2 = findViewById(R.id.button2)
        button3 = findViewById(R.id.button3)
        button4 = findViewById(R.id.button4)
        button5 = findViewById(R.id.button5)
        button6 = findViewById(R.id.button6)
        button7 = findViewById(R.id.button7)
        button8 = findViewById(R.id.button8)
        button9 = findViewById(R.id.button9)
        buttonPlus = findViewById(R.id.buttonPlus)
        buttonSubtract = findViewById(R.id.buttonSubtract)
        buttonMultiply = findViewById(R.id.buttonMultiply)
        buttonDivide = findViewById(R.id.buttonDivide)
        buttonEquals = findViewById(R.id.buttonEquals)
        buttonClear = findViewById(R.id.buttonClear)
        buttonDot = findViewById(R.id.buttonDot)
        buttonBackspace = findViewById(R.id.buttonBackspace)
        button0.setOnClickListener(this)
        button1.setOnClickListener(this)
        button2.setOnClickListener(this)
        button3.setOnClickListener(this)
        button4.setOnClickListener(this)
        button5.setOnClickListener(this)
        button6.setOnClickListener(this)
        button7.setOnClickListener(this)
        button8.setOnClickListener(this)
        button9.setOnClickListener(this)
        buttonPlus.setOnClickListener(this)
        buttonSubtract.setOnClickListener(this)
        buttonMultiply.setOnClickListener(this)
        buttonDivide.setOnClickListener(this)
        buttonEquals.setOnClickListener(this)
        buttonClear.setOnClickListener(this)
        buttonDot.setOnClickListener(this)
        buttonBackspace.setOnClickListener(this)
        operators += '+'
        operators += '-'
        operators += '/'
        operators += '*'

        button0.setOnLongClickListener(this)
        button5.setOnLongClickListener(this)
        button7.setOnLongClickListener(this)

    }

    override fun onClick(v: View?) {
        when(v) {
            button0 -> action("0")
            button1 -> action("1")
            button2 -> action("2")
            button3 -> action("3")
            button4 -> action("4")
            button5 -> action("5")
            button6 -> action("6")
            button7 -> action("7")
            button8 -> action("8")
            button9 -> action("9")
            buttonDivide -> action("/")
            buttonMultiply -> action("*")
            buttonSubtract -> action("-")
            buttonPlus -> action("+")
            buttonBackspace -> action("B")
            buttonDot -> action(".")
            buttonClear -> action("C")
            buttonEquals -> action("=")
        }

    }

    private fun action(input: String) {

        when (input) {
            "C" -> {
                text = "0"
                currentOperator = NONE
            }

            "B" -> {

                val textLength = text.length
                text = if (textLength<2) {
                    "0"
                } else {
                    text.substring(0,textLength-1)
                }
                val operatorPos = text.indexOfAny(operators)
                if (operatorPos==-1) {
                    currentOperator = NONE
                }

            }

            "+" -> {
                if (currentOperator==NONE) {
                    currentOperator=PLUS
                    text += input
                }
            }

            "-" -> {
                if (currentOperator==NONE) {
                    currentOperator=SUBTRACT
                    text += input
                }
            }

            "/" -> {
                if (currentOperator==NONE) {
                    currentOperator=DIVIDE
                    text += input
                }
            }
            "*" -> {
                if (currentOperator==NONE) {
                    currentOperator=MULTIPLY
                    text += input
                }
            }

            "=" -> {

                evaluateSum(text)

            }

            "." -> {
                if (text == "0" || text == "E") text = ""
                text += if (countOccurrences(text, '.') == 3) {
                    ":"
                } else {
                    "."
                }
            }

            else -> {
                if (text == "0" || text == "E") text = ""
                text += input
            }
        }

        display.text = text
    }

    private fun countOccurrences(s: String, ch: Char): Int {
        return s.length - s.replace(ch.toString(), "").length
    }

    private fun connect(connect: String) {
        text = "Destination set: ${connect.replace(":"," Port ")}"
        val intent = Intent(this, CalcService::class.java)
        intent.action = "stop"
        startForegroundService(intent)
        val strings = connect.split(":")
        val portString = strings[1]
        val port = portString.toInt()

        val prefs: SharedPreferences = getSharedPreferences("fileDataTest", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("ip", strings[0])
        editor.putInt("port", port)
        editor.commit()

    }

    private fun evaluateSum(sum: String) {

        if (sum.indexOf(':')>-1) {
            Log.i("Calc", "Connect")
            connect(sum)
            return
        }

        val firstOperatorPos = sum.indexOfAny(operators)
        if (firstOperatorPos==-1) {
            return
        }
        var operatorPos = sum.indexOfAny(operators, firstOperatorPos+1)

        if (operatorPos==-1) operatorPos = firstOperatorPos

        val first = sum.substring(0,operatorPos)
        val second = sum.substring(operatorPos+1)

        var a = 0f
        var b = 0f

        try {
            a = first.toFloat()
            b = second.toFloat()
        } catch (e: NumberFormatException) {
            text = "E"
            currentOperator = NONE
            display.text = text
            return
        }

        Log.i("Calc", "first: $a second: $b")

        var answer = 0f

        when (currentOperator) {
            PLUS -> answer = a + b
            SUBTRACT -> answer = a-b
            MULTIPLY -> answer = a*b
            DIVIDE -> answer = a/b

        }

        if (answer == Float.POSITIVE_INFINITY) {
            text = "E"
            currentOperator = NONE
            display.text = text
            return
        }
        text = ""+answer
        if (text.endsWith(".0")) {
            text = text.substring(0, text.length-2)
        }
        display.text = text
        currentOperator = NONE

    }

    override fun onLongClick(v: View?): Boolean {
        if (v==button5) {
            val intent = Intent(this, CalcService::class.java)
            intent.action="cloud"
            startForegroundService(intent)
        }
        if (v==button7) {
            val intent = Intent(this, CalcService::class.java)
            intent.action="pi"
            startForegroundService(intent)
        }
        if (v==button0) {
            val intent = Intent(this, CalcService::class.java)
            intent.action="stop"
            startForegroundService(intent)
        }
        return true
    }
}