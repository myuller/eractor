Eractor
=======

Non-exhaustive Erlang style actors for Scala Akka framework on top of delimited continuations.

## Example

```scala
class PingPong extends Actor with Eractor {
    var pings = 0
    var pongs = 0

    def body = {

        react {
            case 'ping =>
                pings += 1
                self ! 'pong
        }

        react {
            case 'pong =>
                pongs += 1
        }

        assert(pings == pongs)

        body // loop forever
    }
}

val pingPong = system.actorOf(Props(new PingPong))
(1 to 1000).foreach(_ => pingPong ! 'ping)
```

## Caveats

Unfortunately, Eractor API didn't come out as clean
as in "Erlang semantics: Now in Scala", so here are some
things to watch out for when using library:

* "body" and "react" functions of Eractor are implemented
  using Scala delimited continuations compiler plugin (and
  basically stand for "reset" and "shift" respectively).
  There is a good explanation of continuations and their
  usage limitations at http://jim-mcbeath.blogspot.ru/2010/08/delimited-continuations.html

* Matches in "react" are non-exhaustive which means that
  if incoming message doesn't match anything it will
  be put in queue to be tried later. This fact has important
  consequence: you cannot trust "sender" property of actor,
  which holds reference to last message _received_, not the message
  you are reacting upon right now.
  To provide workaround method "realSender" was introduced,
  which contains captured sender when called from body inside or after
  "react" method's partial function.

* You cannot do nested reacts in current implementation
  of library, so following will not compile:

```scala
    ...
    body = {
        react {
            case 'something =>
                ...
                react {
                    case 'otherThing =>
                        ...
                }
        }
    }
```

but there's workaround for such cases involving use of special helper function __escape__.
If result of this function call is returned from partial function,
the "react" method will never return control to actor body, but instead function passed as
"escape" argument will become new actor body. Example:

```scala
...
var state = List.empty[Int]

def body = {

    state ::= 11

    process
}

def process: Unit @eractorUnit = {
    react {
        case x:Int=>
            if (x > 3)
                escape(finish)
            else
                state ::= x
    }

    process
}

def finish = {
    state ::= 23
}
...
```

* To use library in your code you will need to enable "delimited continuations"
  Scala compiler plugin

## License

This project is licensed under Apache 2.0 License
