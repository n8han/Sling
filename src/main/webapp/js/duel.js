function makeHtml(md) {
  return new Showdown.converter().makeHtml(dueval(md)) 
}

function dueval(str) {
  var re = /#{[^}]*}/g
  
  var text = str.split(re)
  var code = str.match(re) || []
  var out = [], t = true, c = true
  while (t || c) {
    if (t = text.shift()) out.push(t)
    if (c = code.shift()) out.push(eval(c.substr(1)))
  }
  return out.join("")
}

function my_eval(str) {
  return eval(str)
}
