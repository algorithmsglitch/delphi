{ Test 1: Basic Classes, Constructors, Destructors, Encapsulation }
program Test1;

type
  TPerson = class
  private
    FName: String;
    FAge: Integer;
  public
    constructor Create(Name: String; Age: Integer);
    destructor Destroy;
    procedure SetName(Name: String);
    procedure SetAge(Age: Integer);
    function GetName: String;
    function GetAge: Integer;
    procedure PrintInfo;
  end;

constructor TPerson.Create(Name: String; Age: Integer);
begin
  FName := Name;
  FAge := Age;
end;

destructor TPerson.Destroy;
begin
  WriteLn('Destroying person: ', FName);
end;

procedure TPerson.SetName(Name: String);
begin
  FName := Name;
end;

procedure TPerson.SetAge(Age: Integer);
begin
  if Age > 0 then
    FAge := Age
  else
    WriteLn('Error: Age must be positive');
end;

function TPerson.GetName: String;
begin
  Result := FName;
end;

function TPerson.GetAge: Integer;
begin
  Result := FAge;
end;

procedure TPerson.PrintInfo;
begin
  WriteLn('Name: ', FName);
  WriteLn('Age: ', FAge);
end;

var
  Person: TPerson;

begin
  WriteLn('=== Test 1: Classes, Constructors, Encapsulation ===');
  WriteLn('');

  Person := TPerson.Create('Alice', 30);
  WriteLn('Created person.');
  Person.PrintInfo;

  WriteLn('');
  WriteLn('Updating age to 31...');
  Person.SetAge(31);
  WriteLn('New age: ', Person.GetAge);

  WriteLn('');
  WriteLn('Trying to set negative age...');
  Person.SetAge(-5);

  WriteLn('');
  WriteLn('Changing name to Bob...');
  Person.SetName('Bob');
  WriteLn('Name is now: ', Person.GetName);

  WriteLn('');
  WriteLn('Test 1 Complete!');
end.
