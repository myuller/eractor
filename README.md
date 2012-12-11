Eractor
=======

Non-exhaustive Erlang style actors for scala Akka framework on top of delimited continuations.

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

TODO:

* continuations control flow
* no nested shifts (reacts)
* realSender
* enable plugin